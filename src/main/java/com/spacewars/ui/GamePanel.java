package com.spacewars.ui;

import com.spacewars.engine.GameWorld;
import com.spacewars.model.Bullet;
import com.spacewars.model.Enemy;
import com.spacewars.model.Entity;
import com.spacewars.model.Player;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/** Pure rendering + input; every position it draws is a volatile read from a live pool task. */
public class GamePanel extends JPanel {

    private static final double PLAYER_STEP = 8.0;

    /**
     * How long a dying entity's X -&gt; O -&gt; vanish animation plays, kept equal to
     * {@code GameWorld.DESTROYED_ROW_LIFETIME_MS} (the game keeps the entity around for exactly
     * this long after death) and close to {@code VisualThreadPool.DONE_ROW_LIFETIME_MS}, so the
     * on-screen death animation and the sidebar's "thread finished" flash read as one event.
     */
    private static final long DEATH_ANIM_MS = 700;

    private final GameWorld world;

    public GamePanel(GameWorld world) {
        this.world = world;
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> world.getPlayer().moveBy(-PLAYER_STEP);
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> world.getPlayer().moveBy(PLAYER_STEP);
                    case KeyEvent.VK_SPACE -> world.fireBullet();
                    default -> {
                    }
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawPlayer(g2, world.getPlayer());
        for (Enemy enemy : world.enemiesSnapshot()) {
            drawEnemy(g2, enemy);
        }
        for (Bullet bullet : world.bulletsSnapshot()) {
            drawEntity(g2, bullet, Color.YELLOW);
        }

        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        g2.drawString("Score: " + world.getScore(), 12, 22);
        g2.drawString("Missed: " + world.getMissed(), 12, 42);
    }

    private void drawPlayer(Graphics2D g2, Player player) {
        g2.setColor(Color.CYAN);
        g2.fillRect((int) player.getX(), player.getY(), Player.WIDTH, Player.HEIGHT);
    }

    private void drawEnemy(Graphics2D g2, Enemy enemy) {
        if (enemy.isDestroyed()) {
            drawDeathAnimation(g2, enemy);
            return;
        }
        int x = (int) enemy.getX();
        int y = (int) enemy.getY();
        Color fill = switch (enemy.kind()) {
            case RED -> Color.decode("#ff5555");
            case BLUE -> enemy.isFrozen() ? Color.decode("#bde0fe") : Color.decode("#3a86ff");
        };
        g2.setColor(fill);
        g2.fillRect(x, y, enemy.width(), enemy.height());
        if (enemy.isFrozen()) {
            g2.setColor(Color.WHITE);
            g2.drawRect(x, y, enemy.width() - 1, enemy.height() - 1);
        }
    }

    private void drawEntity(Graphics2D g2, Entity entity, Color color) {
        if (entity.isDestroyed()) {
            drawDeathAnimation(g2, entity);
            return;
        }
        g2.setColor(color);
        g2.fillRect((int) entity.getX(), (int) entity.getY(), entity.width(), entity.height());
    }

    /**
     * First half of the entity's remaining lifetime: a red X (marked for destruction). Second
     * half: an orange O, matching the sidebar's DONE-state color, i.e. "thread finished". Fades
     * out via alpha across the whole animation so it visibly vanishes rather than popping away
     * the instant GameWorld prunes it from the entity list.
     */
    private void drawDeathAnimation(Graphics2D g2, Entity entity) {
        long elapsed = System.currentTimeMillis() - entity.destroyedAt();
        if (elapsed >= DEATH_ANIM_MS) {
            return;
        }
        double progress = elapsed / (double) DEATH_ANIM_MS;
        boolean showX = progress < 0.5;
        float alpha = (float) Math.max(0.0, Math.min(1.0, 1.0 - progress));

        int cx = (int) entity.getX() + entity.width() / 2;
        int cy = (int) entity.getY() + entity.height() / 2;
        int glyphSize = Math.max(entity.width(), entity.height()) + 10;

        Composite originalComposite = g2.getComposite();
        Font originalFont = g2.getFont();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setColor(showX ? Color.decode("#ff5555") : Color.decode("#ffa726"));
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, glyphSize));
        String glyph = showX ? "X" : "O";
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(glyph, cx - metrics.stringWidth(glyph) / 2, cy + (metrics.getAscent() - metrics.getDescent()) / 2);
        g2.setFont(originalFont);
        g2.setComposite(originalComposite);
    }
}
