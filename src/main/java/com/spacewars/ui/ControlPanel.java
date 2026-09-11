package com.spacewars.ui;

import com.spacewars.pool.VisualThreadPool;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.Color;
import java.awt.Component;

/** Live sliders wired straight to {@code ThreadPoolExecutor.setCorePoolSize/setMaximumPoolSize}. */
public class ControlPanel extends JPanel {

    public ControlPanel(VisualThreadPool enemyPool, VisualThreadPool bulletPool, Runnable onRestartPools) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(0x1e1e1e));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        add(poolSliderGroup("Enemy pool size", enemyPool));
        add(poolSliderGroup("Bullet pool size", bulletPool));

        JButton restartButton = new JButton("Shutdown & restart pools");
        restartButton.setFocusable(false);
        restartButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        restartButton.addActionListener(e -> onRestartPools.run());
        add(restartButton);

        JLabel hint = new JLabel("<html><font color='gray'>Arrow keys / A,D to move &middot; Space to fire</font></html>");
        add(hint);
    }

    private JPanel poolSliderGroup(String title, VisualThreadPool pool) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(0x1e1e1e));

        JLabel label = new JLabel(title);
        label.setForeground(Color.WHITE);
        panel.add(label);

        JSlider maxSlider = new JSlider(1, 12, pool.maxPoolSize());
        maxSlider.setBackground(new Color(0x1e1e1e));
        maxSlider.setMajorTickSpacing(1);
        maxSlider.setPaintTicks(true);
        // Keeps keyboard focus on the game canvas: a focusable slider steals arrow keys/space
        // as soon as it's clicked, and Swing never gives focus back to GamePanel on its own.
        maxSlider.setFocusable(false);
        maxSlider.addChangeListener(e -> {
            int max = maxSlider.getValue();
            int core = Math.min(pool.corePoolSize(), max);
            pool.resize(core, max);
        });
        panel.add(maxSlider);

        return panel;
    }
}
