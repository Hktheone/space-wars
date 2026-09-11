package com.spacewars.pool;

/** Immutable point-in-time view of one pool worker thread, for painting a panel row. */
public record WorkerSnapshot(String threadName, WorkerState state, String currentTask, long since) {
}
