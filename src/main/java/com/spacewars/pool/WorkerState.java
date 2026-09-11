package com.spacewars.pool;

/** The states this demo renders - a simplified view of the real {@link java.lang.Thread.State}. */
public enum WorkerState {
    IDLE,
    BUSY,
    /** Task just returned; shown briefly before falling back to IDLE. The worker thread itself is still alive. */
    DONE,
    TERMINATED
}
