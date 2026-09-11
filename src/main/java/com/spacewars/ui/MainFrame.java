package com.spacewars.ui;

import com.spacewars.engine.GameWorld;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

public class MainFrame extends JFrame {

    private static final int GAME_WIDTH = 640;
    private static final int GAME_HEIGHT = 640;
    private static final int TICK_MS = 30;
    private static final int SPAWN_MS = 900;

    public MainFrame() {
        super("Space Wars - Thread Pool Visualizer");

        GameWorld world = new GameWorld(GAME_WIDTH, GAME_HEIGHT);

        GamePanel gamePanel = new GamePanel(world);
        gamePanel.setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));

        ThreadPoolPanel threadPoolPanel = new ThreadPoolPanel(world.getEnemyPool(), world.getBulletPool(), world::stuckEnemyCount);
        ControlPanel controlPanel = new ControlPanel(world.getEnemyPool(), world.getBulletPool(), world::restartPools);
        LegendPanel legendPanel = new LegendPanel();

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(0x1e1e1e));
        sidebar.add(legendPanel, BorderLayout.NORTH);
        sidebar.add(threadPoolPanel, BorderLayout.CENTER);
        sidebar.add(controlPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(gamePanel, BorderLayout.CENTER);
        add(sidebar, BorderLayout.EAST);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);

        Timer gameTimer = new Timer(TICK_MS, e -> {
            world.tick();
            gamePanel.repaint();
            threadPoolPanel.repaint();
        });
        gameTimer.start();

        Timer spawnTimer = new Timer(SPAWN_MS, e -> world.spawnEnemy());
        spawnTimer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                gameTimer.stop();
                spawnTimer.stop();
                world.shutdown();
            }
        });

        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }
}
