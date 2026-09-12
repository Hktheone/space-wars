package com.spacewars.ui;

import com.spacewars.pool.VisualThreadPool;
import com.spacewars.pool.WorkerSnapshot;
import com.spacewars.pool.WorkerState;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Renders both pools' live worker state, sourced entirely from {@link VisualThreadPool}'s
 * introspection methods - nothing here is a guess about what the executor is doing.
 */
public class ThreadPoolPanel extends JPanel {

    private static final int ROW_HEIGHT = 22;
    private static final long REJECTED_DISPLAY_MS = 2500;

    private final VisualThreadPool enemyPool;
    private final VisualThreadPool bulletPool;
    private final LongSupplier stuckEnemyCount;

    public ThreadPoolPanel(VisualThreadPool enemyPool, VisualThreadPool bulletPool, LongSupplier stuckEnemyCount) {
        this.enemyPool = enemyPool;
        this.bulletPool = bulletPool;
        this.stuckEnemyCount = stuckEnemyCount;
        setBackground(new Color(0x1e1e1e));
        setPreferredSize(new java.awt.Dimension(280, 0));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        int y = 10;
        y = drawPool(g2, "ENEMY POOL", enemyPool, y, stuckEnemyCount.getAsLong());
        y += 14;
        drawPool(g2, "BULLET POOL", bulletPool, y, -1);
    }

    private int drawPool(Graphics2D g2, String title, VisualThreadPool pool, int startY, long stuckCount) {
        int y = startY;
        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
        g2.drawString(title, 10, y);
        y += 18;

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawString(String.format("core=%d max=%d queue=%d/%d",
                pool.corePoolSize(), pool.maxPoolSize(), pool.queueDepth(), pool.queueCapacity()), 10, y);
        y += 16;
        g2.drawString(String.format("completed=%d rejected=%d", pool.completedCount(), pool.rejectedCount()), 10, y);
        y += 16;

        if (stuckCount >= 0) {
            g2.setColor(stuckCount > 0 ? new Color(0xff7043) : Color.LIGHT_GRAY);
            g2.drawString("stuck (sleeping): " + stuckCount, 10, y);
            y += 16;
        }

        long now = System.currentTimeMillis();
        if (pool.lastRejectedLabel() != null && now - pool.lastRejectedAt() < REJECTED_DISPLAY_MS) {
            g2.setColor(new Color(0xef5350));
            g2.drawString("rejected: " + pool.lastRejectedLabel() + " (queue full)", 10, y);
            y += 16;
        }

        y += 2;
        List<WorkerSnapshot> rows = pool.snapshot();
        for (WorkerSnapshot row : rows) {
            drawRow(g2, row, y);
            y += ROW_HEIGHT;
        }
        return y;
    }

    private void drawRow(Graphics2D g2, WorkerSnapshot row, int y) {
        Color dot = switch (row.state()) {
            case IDLE -> Color.GRAY;
            case BUSY -> new Color(0x4caf50);
            case DONE -> new Color(0xffa726);
            case TERMINATED -> Color.RED;
        };
        g2.setColor(dot);
        g2.fillOval(12, y - 9, 8, 8);

        g2.setColor(row.state() == WorkerState.TERMINATED ? Color.GRAY : Color.WHITE);
        String text = switch (row.state()) {
            case IDLE -> row.threadName() + ": idle";
            case BUSY, DONE -> row.threadName() + ": " + row.currentTask();
            case TERMINATED -> row.threadName() + ": terminated";
        };
        g2.drawString(text, 26, y);

        if (row.state() == WorkerState.TERMINATED) {
            int textWidth = g2.getFontMetrics().stringWidth(text);
            g2.setColor(Color.RED);
            g2.drawLine(26, y - 4, 26 + textWidth, y - 4);
        }
    }
}
