package com.spacewars.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base for anything rendered on screen whose lifecycle is driven by a pool worker thread
 * (see {@link Enemy} and {@link Bullet}). Position and destroyed-state are read from the
 * Swing repaint timer while being written from a worker thread, so both are volatile rather
 * than synchronized - a visualization can tolerate the rare stale read, a real system wouldn't.
 */
public abstract class Entity {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    /** Why a task's run() returned - drives what the thread-pool sidebar says when it does. */
    public enum EndCause {
        KILLED,
        OFFSCREEN
    }

    protected final int id;
    protected volatile double x;
    protected volatile double y;
    private volatile boolean destroyed;
    private volatile long destroyedAt;
    private volatile EndCause endCause;

    protected Entity(double x, double y) {
        this.id = SEQUENCE.incrementAndGet();
        this.x = x;
        this.y = y;
    }

    public int getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public long destroyedAt() {
        return destroyedAt;
    }

    public EndCause endCause() {
        return endCause;
    }

    /** Marks this entity destroyed with a specific cause; safe to call more than once (first cause wins). */
    public void destroy(EndCause cause) {
        if (!destroyed) {
            destroyed = true;
            destroyedAt = System.currentTimeMillis();
            endCause = cause;
        }
    }

    /** Natural end-of-life (reached the bottom/top of screen, or collided with the player) - not a kill. */
    public void destroy() {
        destroy(EndCause.OFFSCREEN);
    }

    public abstract String label();

    public abstract int width();

    public abstract int height();
}
