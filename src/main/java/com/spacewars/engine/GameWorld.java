package com.spacewars.engine;

import com.spacewars.model.Bullet;
import com.spacewars.model.Enemy;
import com.spacewars.model.EnemyKind;
import com.spacewars.model.Entity;
import com.spacewars.model.Player;
import com.spacewars.pool.VisualThreadPool;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns both pools and the shared entity lists. {@code tick()} runs on the Swing timer thread
 * (effectively the EDT) and only ever reads volatile entity state and mutates the
 * {@link CopyOnWriteArrayList}s - the actual position updates happen inside each entity's own
 * {@code run()} on its pool worker thread.
 */
public class GameWorld {

    // Kept equal to GamePanel.DEATH_ANIM_MS and close to VisualThreadPool.DONE_ROW_LIFETIME_MS
    // so the on-screen X/O death animation, and the sidebar's "thread finished" flash, read as one event.
    private static final long DESTROYED_ROW_LIFETIME_MS = 700;
    private static final long MIN_FIRE_INTERVAL_MS = 220;
    private static final double BLUE_SPAWN_CHANCE = 0.35;

    private final int screenWidth;
    private final int screenHeight;

    private final VisualThreadPool enemyPool = new VisualThreadPool("Enemy", 4, 6, 20);
    private final VisualThreadPool bulletPool = new VisualThreadPool("Bullet", 3, 5, 20);

    private final CopyOnWriteArrayList<Enemy> enemies = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final Player player;

    private final AtomicInteger score = new AtomicInteger(0);
    private final AtomicInteger missed = new AtomicInteger(0);

    private volatile long lastFireAt = 0;
    private volatile boolean spawningEnabled = true;

    public GameWorld(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.player = new Player(screenWidth, screenHeight);
    }

    public void spawnEnemy() {
        if (!spawningEnabled) {
            return;
        }
        double x = ThreadLocalRandom.current().nextDouble(0, screenWidth - Enemy.WIDTH);
        EnemyKind kind = ThreadLocalRandom.current().nextDouble() < BLUE_SPAWN_CHANCE ? EnemyKind.BLUE : EnemyKind.RED;
        Enemy enemy = new Enemy(x, screenHeight, kind);
        // Only tracked as a game entity if the pool actually accepted it - a rejected task never
        // becomes a thread, so it has no lifecycle to animate; it just never spawns.
        if (enemyPool.submit(enemy, enemy::statusLabel)) {
            enemies.add(enemy);
        }
    }

    public void fireBullet() {
        long now = System.currentTimeMillis();
        if (now - lastFireAt < MIN_FIRE_INTERVAL_MS) {
            return;
        }
        lastFireAt = now;
        double x = player.getX() + Player.WIDTH / 2.0 - Bullet.WIDTH / 2.0;
        Bullet bullet = new Bullet(x, player.getY());
        if (bulletPool.submit(bullet, bullet::statusLabel)) {
            bullets.add(bullet);
        }
    }

    /** Collision detection, scoring, and pruning of long-dead entities. Called ~30 times/sec. */
    public void tick() {
        for (Bullet bullet : bullets) {
            if (bullet.isDestroyed()) {
                continue;
            }
            for (Enemy enemy : enemies) {
                if (enemy.isDestroyed()) {
                    continue;
                }
                if (overlaps(bullet, enemy)) {
                    bullet.destroy(Entity.EndCause.KILLED);
                    boolean wasFrozen = enemy.isFrozen();
                    enemy.hit();
                    if (enemy.isDestroyed()) {
                        score.addAndGet(10);
                    } else if (enemy.isFrozen() && !wasFrozen) {
                        score.addAndGet(3);
                    }
                    break;
                }
            }
        }

        for (Enemy enemy : enemies) {
            if (!enemy.isDestroyed() && enemy.getY() + enemy.height() >= player.getY()
                    && overlapsX(enemy, player.getX(), Player.WIDTH)) {
                enemy.destroy();
                missed.incrementAndGet();
            }
        }

        long now = System.currentTimeMillis();
        enemies.removeIf(e -> e.isDestroyed() && now - e.destroyedAt() > DESTROYED_ROW_LIFETIME_MS);
        bullets.removeIf(b -> b.isDestroyed() && now - b.destroyedAt() > DESTROYED_ROW_LIFETIME_MS);
    }

    private boolean overlaps(Entity a, Entity b) {
        return a.getX() < b.getX() + b.width()
                && a.getX() + a.width() > b.getX()
                && a.getY() < b.getY() + b.height()
                && a.getY() + a.height() > b.getY();
    }

    private boolean overlapsX(Entity a, double x, int width) {
        return a.getX() < x + width && a.getX() + a.width() > x;
    }

    public void setSpawningEnabled(boolean enabled) {
        this.spawningEnabled = enabled;
    }

    public List<Enemy> enemiesSnapshot() {
        return enemies;
    }

    public List<Bullet> bulletsSnapshot() {
        return bullets;
    }

    public Player getPlayer() {
        return player;
    }

    public VisualThreadPool getEnemyPool() {
        return enemyPool;
    }

    public VisualThreadPool getBulletPool() {
        return bulletPool;
    }

    public int getScore() {
        return score.get();
    }

    public int getMissed() {
        return missed.get();
    }

    /**
     * Enemies currently frozen - each one is a worker held by a task that's sleeping instead of
     * falling. They self-recover (thaw) after a few seconds unless killed first; this count is a
     * live snapshot of how many workers are tied up that way right now, not a permanent loss.
     */
    public long stuckEnemyCount() {
        return enemies.stream().filter(e -> !e.isDestroyed() && e.isFrozen()).count();
    }

    /**
     * Shuts both pools down immediately (interrupting every running task) and rebuilds them fresh
     * at their current core/max size, clearing the board. Demonstrates pool teardown/rebuild as
     * its own lifecycle event, distinct from any single task or thread ending.
     */
    public void restartPools() {
        enemyPool.restart();
        bulletPool.restart();
        enemies.clear();
        bullets.clear();
    }

    public void shutdown() {
        enemyPool.shutdown();
        bulletPool.shutdown();
    }
}
