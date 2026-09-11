package com.spacewars.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An enemy is a task, not a thread: its whole time on screen happens inside {@link #run()},
 * which occupies one pool worker for as long as the enemy is alive. If the enemy pool is
 * saturated, newly spawned enemies simply wait in the executor's queue - invisible above the
 * top of the screen - until a worker frees up, which is the entire point of the demo.
 *
 * <p>RED enemies die on the first bullet hit. BLUE enemies freeze on the first hit instead:
 * {@link #run()} stops advancing y and just keeps sleeping in place, so the worker thread is
 * genuinely parked (repeated {@code Thread.sleep}, i.e. real {@code TIMED_WAITING}) rather than
 * finishing. A second hit while frozen destroys it and finally frees the worker. A frozen
 * enemy that's never hit again never releases its worker for the rest of the game.
 */
public class Enemy extends Entity implements Runnable {

    public static final int WIDTH = 30;
    public static final int HEIGHT = 24;

    private static final double SPEED_PX_PER_TICK = 3.0;
    private static final int TICK_MS = 40;

    private static final long FREEZE_DURATION_MS = 5000;

    private final int screenHeight;
    private final EnemyKind kind;
    private final AtomicInteger hitCount = new AtomicInteger(0);
    private volatile boolean frozen = false;
    private volatile long frozenAt = 0;

    public Enemy(double x, int screenHeight, EnemyKind kind) {
        super(x, -HEIGHT);
        this.screenHeight = screenHeight;
        this.kind = kind;
    }

    /** Called from the collision-detection tick, on the EDT timer thread - not this enemy's own worker thread. */
    public void hit() {
        hitCount.incrementAndGet();
        if (kind == EnemyKind.RED || frozen) {
            destroy(EndCause.KILLED);
        } else {
            frozen = true;
            frozenAt = System.currentTimeMillis();
        }
    }

    public boolean isFrozen() {
        return frozen;
    }

    public EnemyKind kind() {
        return kind;
    }

    /**
     * Live status text for the thread-pool sidebar: base label while falling, "sleep(Ns)" counting
     * down while frozen, or - once this task's run() has actually returned - a cause-specific
     * terminal message so a kill reads differently from just running off the bottom of the screen.
     */
    public String statusLabel() {
        if (isDestroyed()) {
            return endCause() == EndCause.KILLED
                    ? label() + " killed - thread finished"
                    : label() + " thread finished";
        }
        if (!frozen) {
            return label();
        }
        long remainingMs = FREEZE_DURATION_MS - (System.currentTimeMillis() - frozenAt);
        long remainingSec = Math.max(0, (remainingMs + 999) / 1000);
        return label() + " sleep(" + remainingSec + ")";
    }

    @Override
    public void run() {
        try {
            while (!isDestroyed() && y < screenHeight) {
                if (frozen) {
                    if (System.currentTimeMillis() - frozenAt >= FREEZE_DURATION_MS) {
                        frozen = false;
                    }
                } else {
                    y += SPEED_PX_PER_TICK;
                }
                Thread.sleep(TICK_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            destroy();
        }
    }

    @Override
    public String label() {
        return "Enemy#" + id;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }
}
