package com.spacewars.pool;

import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Names each worker deterministically ("Enemy-Worker-1", ...) and records its real birth/death
 * in {@code statusByThreadId}. The death record is what makes {@link WorkerState#TERMINATED}
 * genuine: the wrapping lambda's finally block only runs when the JVM thread's run() method is
 * about to return for good - e.g. the pool shrank past corePoolSize and this worker idled out
 * past keepAliveTime, or the pool shut down.
 */
public class TrackingThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final Map<Long, WorkerSnapshot> statusByThreadId;

    public TrackingThreadFactory(String prefix, Map<Long, WorkerSnapshot> statusByThreadId) {
        this.prefix = prefix;
        this.statusByThreadId = statusByThreadId;
    }

    @Override
    public Thread newThread(Runnable poolWorker) {
        String name = prefix + "-Worker-" + counter.incrementAndGet();
        Thread thread = new Thread(() -> {
            try {
                poolWorker.run();
            } finally {
                Thread current = Thread.currentThread();
                statusByThreadId.put(current.threadId(),
                        new WorkerSnapshot(current.getName(), WorkerState.TERMINATED, "-", System.currentTimeMillis()));
            }
        }, name);
        thread.setDaemon(true);
        statusByThreadId.put(thread.threadId(), new WorkerSnapshot(name, WorkerState.IDLE, "-", System.currentTimeMillis()));
        return thread;
    }
}
