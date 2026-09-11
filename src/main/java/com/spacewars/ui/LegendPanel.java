package com.spacewars.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Purely explanatory: maps this demo's simplified {@link com.spacewars.pool.WorkerState} rows
 * back to the real {@link Thread.State} values they stand in for, so the sidebar's colored dots
 * connect to actual JVM concepts instead of reading as an invented color code.
 */
public class LegendPanel extends JPanel {

    private static final String[][] ROWS = {
            {"IDLE", "0x9e9e9e", "RUNNABLE, waiting on empty queue"},
            {"BUSY", "0x4caf50", "RUNNABLE, or TIMED_WAITING if frozen"},
            {"DONE", "0xffa726", "RUNNABLE - run() about to return"},
            {"TERMINATED", "0xff0000", "run() exited; JVM thread is gone"},
    };

    public LegendPanel() {
        setBackground(new Color(0x1e1e1e));
        setPreferredSize(new Dimension(280, 172));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int y = 16;
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g2.drawString("Thread.State reference", 10, y);
        y += 20;

        for (String[] row : ROWS) {
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            g2.setColor(Color.decode(row[1]));
            g2.fillOval(12, y - 8, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(row[0], 26, y);
            y += 15;

            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString(row[2], 26, y);
            y += 21;
        }
    }
}
