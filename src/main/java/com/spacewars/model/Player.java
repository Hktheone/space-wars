package com.spacewars.model;

/** The player is drawn and moved directly on the Event Dispatch Thread - it has no task/thread of its own. */
public class Player {

    public static final int WIDTH = 40;
    public static final int HEIGHT = 24;

    private final int screenWidth;
    private final int y;
    private volatile double x;

    public Player(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.y = screenHeight - 50;
        this.x = (screenWidth - WIDTH) / 2.0;
    }

    public void moveBy(double dx) {
        x = Math.max(0, Math.min(screenWidth - WIDTH, x + dx));
    }

    public double getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
