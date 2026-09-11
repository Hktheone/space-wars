package com.spacewars.model;

/** Same idea as {@link Enemy} but shorter-lived and moving upward - its own pool, its own pressure. */
public class Bullet extends Entity implements Runnable {

    public static final int WIDTH = 4;
    public static final int HEIGHT = 10;

    private static final double SPEED_PX_PER_TICK = 8.0;
    private static final int TICK_MS = 20;

    public Bullet(double x, double y) {
        super(x, y);
    }

    @Override
    public void run() {
        try {
            while (!isDestroyed() && y > -HEIGHT) {
                y -= SPEED_PX_PER_TICK;
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
        return "Bullet#" + id;
    }

    /** Mirrors {@link Enemy#statusLabel()}: a kill reads differently from just leaving the screen. */
    public String statusLabel() {
        if (!isDestroyed()) {
            return label();
        }
        return endCause() == EndCause.KILLED
                ? label() + " killed - thread finished"
                : label() + " thread finished";
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
