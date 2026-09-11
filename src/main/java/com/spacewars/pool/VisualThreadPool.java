package com.spacewars.pool;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Thin wrapper around a real {@link ThreadPoolExecutor} that exists purely to expose what's
 * happening inside it: which worker is doing what, how deep the queue is, how many tasks were
 * rejected outright. Every number here comes from the executor's own introspection API
 * ({@code getActiveCount()}, {@code getQueue()}, {@code getCompletedTaskCount()}) rather than
 * hand-rolled counters, so the panel is reporting ground truth, not a guess.
 */
public class VisualThreadPool {

    private static final long TERMINATED_ROW_LIFETIME_MS = 4000;
    private static final long DONE_ROW_LIFETIME_MS = 700;

    private volatile ThreadPoolExecutor executor;
    private final Map<Long, WorkerSnapshot> statusByThreadId = new ConcurrentHashMap<>();
    private final Map<Long, Supplier<String>> labelSuppliers = new ConcurrentHashMap<>();
    private final AtomicInteger rejectedCount = new AtomicInteger(0);
    private final String namePrefix;
    private final int queueCapacity;

    private volatile String lastRejectedLabel;
    private volatile long lastRejectedAt;

    public VisualThreadPool(String namePrefix, int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.namePrefix = namePrefix;
        this.queueCapacity = queueCapacity;
        this.executor = newExecutor(corePoolSize, maxPoolSize);
        executor.prestartAllCoreThreads();
    }

    private ThreadPoolExecutor newExecutor(int corePoolSize, int maxPoolSize) {
        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                5, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new TrackingThreadFactory(namePrefix, statusByThreadId),
                (r, exec) -> {
                    throw new RejectedExecutionException();
                });
    }

    /**
     * Submits a task; if the pool and its bounded queue are both full, it's rejected - counted,
     * remembered for the sidebar's "last rejected" line, and never run - rather than blocked on.
     * Returns false in that case so the caller (e.g. GameWorld) knows not to treat the task's
     * entity as alive: a rejected task never became a thread at all, so there's nothing to animate.
     *
     * <p>{@code labelSupplier} is re-read on every {@link #snapshot()}, not just once at submission,
     * so a task whose visible state changes mid-run (e.g. an enemy freezing) shows that live in the
     * sidebar instead of a stale label frozen at submit time.
     */
    public boolean submit(Runnable task, Supplier<String> labelSupplier) {
        try {
            executor.execute(() -> {
                long threadId = Thread.currentThread().threadId();
                labelSuppliers.put(threadId, labelSupplier);
                statusByThreadId.computeIfPresent(threadId,
                        (id, snapshot) -> new WorkerSnapshot(snapshot.threadName(), WorkerState.BUSY, labelSupplier.get(), System.currentTimeMillis()));
                try {
                    task.run();
                } finally {
                    Supplier<String> finishedLabel = labelSuppliers.remove(threadId);
                    String endedText = finishedLabel != null ? finishedLabel.get() : "task thread finished";
                    statusByThreadId.computeIfPresent(threadId,
                            (id, snapshot) -> snapshot.state() == WorkerState.TERMINATED
                                    ? snapshot
                                    : new WorkerSnapshot(snapshot.threadName(), WorkerState.DONE, endedText, System.currentTimeMillis()));
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            rejectedCount.incrementAndGet();
            lastRejectedLabel = labelSupplier.get();
            lastRejectedAt = System.currentTimeMillis();
            return false;
        }
    }

    /**
     * Shuts the current executor down immediately (interrupting every worker) and replaces it
     * with a brand-new one at the same core/max size, resetting all tracked stats. Unlike
     * {@link #shutdown()}, the pool is usable again right after this returns - this is what backs
     * the UI's "shutdown & restart" button, to demonstrate pool teardown/rebuild rather than a
     * process exit.
     */
    public void restart() {
        int core = executor.getCorePoolSize();
        int max = executor.getMaximumPoolSize();
        executor.shutdownNow();
        statusByThreadId.clear();
        labelSuppliers.clear();
        rejectedCount.set(0);
        lastRejectedLabel = null;
        lastRejectedAt = 0;
        executor = newExecutor(core, max);
        executor.prestartAllCoreThreads();
    }

    public void resize(int corePoolSize, int maxPoolSize) {
        if (maxPoolSize >= executor.getCorePoolSize()) {
            executor.setMaximumPoolSize(maxPoolSize);
            executor.setCorePoolSize(corePoolSize);
        } else {
            executor.setCorePoolSize(corePoolSize);
            executor.setMaximumPoolSize(maxPoolSize);
        }
    }

    /**
     * Rows to paint. Terminated rows are kept briefly (TERMINATED_ROW_LIFETIME_MS) then pruned;
     * DONE rows (a task that just finished on an otherwise-still-alive worker) fall back to IDLE
     * after DONE_ROW_LIFETIME_MS unless the worker has already picked up a new task by then.
     */
    public List<WorkerSnapshot> snapshot() {
        long now = System.currentTimeMillis();
        statusByThreadId.entrySet().removeIf(e ->
                e.getValue().state() == WorkerState.TERMINATED && now - e.getValue().since() > TERMINATED_ROW_LIFETIME_MS);
        statusByThreadId.replaceAll((id, snapshot) ->
                snapshot.state() == WorkerState.DONE && now - snapshot.since() > DONE_ROW_LIFETIME_MS
                        ? new WorkerSnapshot(snapshot.threadName(), WorkerState.IDLE, "-", now)
                        : snapshot);
        return statusByThreadId.entrySet().stream()
                .map(entry -> {
                    WorkerSnapshot snapshot = entry.getValue();
                    if (snapshot.state() != WorkerState.BUSY) {
                        return snapshot;
                    }
                    Supplier<String> supplier = labelSuppliers.get(entry.getKey());
                    String label = supplier != null ? supplier.get() : snapshot.currentTask();
                    return new WorkerSnapshot(snapshot.threadName(), snapshot.state(), label, snapshot.since());
                })
                .sorted(Comparator.comparing(WorkerSnapshot::threadName))
                .collect(Collectors.toList());
    }

    public int corePoolSize() {
        return executor.getCorePoolSize();
    }

    public int maxPoolSize() {
        return executor.getMaximumPoolSize();
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int queueDepth() {
        return executor.getQueue().size();
    }

    public int activeCount() {
        return executor.getActiveCount();
    }

    public long completedCount() {
        return executor.getCompletedTaskCount();
    }

    public int rejectedCount() {
        return rejectedCount.get();
    }

    public String lastRejectedLabel() {
        return lastRejectedLabel;
    }

    public long lastRejectedAt() {
        return lastRejectedAt;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
