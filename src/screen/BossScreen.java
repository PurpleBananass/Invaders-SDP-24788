package screen;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.function.IntSupplier;

import Animations.Explosion;
import engine.Cooldown;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import engine.AchievementManager;
import engine.SoundManager;
import entity.BulletEmitter; // 보스 로직에 필요
import entity.*;

/**
 * Implements the boss screen, where the boss fight happens.
 * Based on GameScreen.java.
 */
public class BossScreen extends Screen {

    /** Milliseconds until the screen accepts user input. */
    private static final int INPUT_DELAY = 6000;
    /** Bonus score for each life remaining at the end of the level. */
    private static final int LIFE_SCORE = 100;
    /** Time from finishing the level to screen change. */
    private static final int SCREEN_CHANGE_INTERVAL = 1500;
    /** Height of the interface separation line. */
    private static final int SEPARATION_LINE_HEIGHT = 68;

    /** For Check Achievement */
    private AchievementManager achievementManager;
    /** Current difficulty level number. */
    private int level;

    /** Boss Entity. */
    private Boss boss;
    /** Formation of boss's minions. */
    private EnemyShipFormation minionFormation;

    /** Formation of player ships. */
    private Ship[] ships = new Ship[GameState.NUM_PLAYERS];
    /** Time from finishing the level to screen change. */
    private Cooldown screenFinishedCooldown;
    /** Set of all bullets fired by on screen ships. */
    private Set<Bullet> bullets;
    /** Set of all items spawned. */
    private Set<Item> items;
    /** Time of game start. */
    private long gameStartTime;
    /** Checks if the level is finished. */
    private boolean levelFinished;
    /** Logger instance. */
    private static final Logger logger = Core.getLogger();

    /** Checks if the game is paused. */
    private boolean isPaused;
    /** Cooldown for pausing. */
    private Cooldown pauseCooldown;
    /** Cooldown for returning to menu. */
    private Cooldown returnMenuCooldown;

    /** checks if player took damage */
    private boolean tookDamageThisLevel;
    /** checks if countdown sound has played */
    private boolean countdownSoundPlayed = false;

    /** Current game state. */
    private final GameState state;

    /** Player ship types. */
    private Ship.ShipType shipTypeP1;
    private Ship.ShipType shipTypeP2;

    /**
     * Constructor, establishes the properties of the screen.
     *
     * @param gameState
     * Current game state.
     * @param width
     * Screen width.
     * @param height
     * Screen height.
     * @param fps
     * Frames per second, frame rate at which the game is run.
     * @param shipTypeP1
     * Player 1's ship type.
     * @param shipTypeP2
     * Player 2's ship type.
     * @param achievementManager
     * Achievement manager instance.
     */
    public BossScreen(final GameState gameState,
                      final int width, final int height, final int fps,
                      final Ship.ShipType shipTypeP1, final Ship.ShipType shipTypeP2,
                      final AchievementManager achievementManager) {
        super(width, height, fps);

        this.state = gameState;
        this.achievementManager = achievementManager;
        this.shipTypeP1 = shipTypeP1;
        this.shipTypeP2 = shipTypeP2;
        this.level = gameState.getLevel();
        this.tookDamageThisLevel = false;
    }

    /**
     * Initializes basic screen properties, and adds necessary elements.
     */
    public final void initialize() {
        super.initialize();

        state.clearAllEffects();

        // Start background music
        SoundManager.startBackgroundMusic("sound/SpaceInvader-GameTheme.wav");

        // 1. Create player ships
        this.ships[0] = new Ship(this.width / 2 - 60, this.height - 30, Entity.Team.PLAYER1, shipTypeP1, this.state);
        this.ships[0].setPlayerId(1);
        if (state.isCoop()) {
            this.ships[1] = new Ship(this.width / 2 + 60, this.height - 30, Entity.Team.PLAYER2, shipTypeP2, this.state);
            this.ships[1].setPlayerId(2);
        } else {
            this.ships[1] = null;
        }

        // Define Boss Y position and minion start Y
        int bossY = SEPARATION_LINE_HEIGHT + 40; // Below UI and HP Bar
        int bossHeight = 30 * 2; // From Boss.java
        int minionStartY = bossY + bossHeight + 20; // 20px padding below boss
        int initMinionY = 100; // Default INIT_POS_Y in EnemyShipFormation
        final int yOffset = minionStartY - initMinionY;

        // 2. Define Boss Callbacks
        Runnable spawnHP1Group = () -> {
            logger.info("Boss spawning Phase 1 minions (5x2).");
            GameSettings minionSettings = new GameSettings(5, 2, 100, 2000); // 5x2
            this.minionFormation = new EnemyShipFormation(minionSettings);
            this.minionFormation.attach(this);
            // Move minions below boss
            for (EnemyShip minion : this.minionFormation) {
                minion.move(0, yOffset);
            }
        };

        Runnable spawnHP2Group = () -> {
            logger.info("Boss spawning Phase 2 minions (5x3).");
            GameSettings minionSettings = new GameSettings(5, 3, 90, 1500); // 5x3
            this.minionFormation = new EnemyShipFormation(minionSettings);
            this.minionFormation.attach(this);
            // Move minions below boss
            for (EnemyShip minion : this.minionFormation) {
                minion.move(0, yOffset);
            }
        };

        Runnable clearShield = () -> {
            logger.info("Boss clearing phase 1 minions.");
            if (this.minionFormation != null) {
                for (EnemyShip minion : this.minionFormation) {
                    minion.destroy();
                }
                // Let update() handle the visual removal
            }
        };

        IntSupplier minionAlive = () -> {
            return (this.minionFormation != null) ? this.minionFormation.getShipCount() : 0;
        };

        BulletEmitter emitter = (x, y, vx, vy) -> {
            // Boss.java's vy is just a concept, use a fixed speed for enemy bullets.
            int bossBulletSpeed = 4; // Standard enemy bullet speed
            Bullet bullet = BulletPool.getBullet(x, y, bossBulletSpeed, 3 * 2, 5 * 2, Entity.Team.ENEMY);
            this.bullets.add(bullet);
        };

        // 3. Create Boss
        int bossX = (this.width / 2) - (50 * 2 / 2); // Boss.java BOSS_WIDTH=50
        this.boss = new Boss(bossX, bossY,this.width, emitter, minionAlive, spawnHP1Group, spawnHP2Group, clearShield);

        // 4. Cooldowns and Sets
        this.screenFinishedCooldown = Core.getCooldown(SCREEN_CHANGE_INTERVAL);
        this.bullets = new HashSet<Bullet>();
        this.items = new HashSet<Item>();

        // Special input delay / countdown.
        this.gameStartTime = System.currentTimeMillis();
        this.inputDelay = Core.getCooldown(INPUT_DELAY);
        this.inputDelay.reset();
        drawManager.setDeath(false);

        this.isPaused = false;
        this.pauseCooldown = Core.getCooldown(300);
        this.returnMenuCooldown = Core.getCooldown(300);
    }

    /**
     * Starts the action.
     *
     * @return Next screen code.
     */
    public final int run() {
        super.run();

        // 2P mode: award bonus score for remaining TEAM lives
        state.addScore(0, LIFE_SCORE * state.getLivesRemaining());

        // Stop all music on exiting this screen
        SoundManager.stopAllMusic();

        this.logger.info("Boss Screen cleared with a score of " + state.getScore());
        return this.returnCode;
    }

    /**
     * Updates the elements on screen and checks for events.
     */
    protected final void update() {
        super.update();

        // Countdown beep
        if (!this.inputDelay.checkFinished() && !countdownSoundPlayed) {
            long elapsed = System.currentTimeMillis() - this.gameStartTime;
            if (elapsed > 1750) {
                SoundManager.playOnce("sound/CountDownSound.wav");
                countdownSoundPlayed = true;
            }
        }

        // Pause logic
        if (this.inputDelay.checkFinished() && inputManager.isKeyDown(KeyEvent.VK_ESCAPE) && this.pauseCooldown.checkFinished()) {
            this.isPaused = !this.isPaused;
            this.pauseCooldown.reset();
            if (this.isPaused) SoundManager.stopBackgroundMusic();
            else SoundManager.startBackgroundMusic("sound/SpaceInvader-GameTheme.wav");
        }
        if (this.isPaused && inputManager.isKeyDown(KeyEvent.VK_BACK_SPACE) && this.returnMenuCooldown.checkFinished()) {
            SoundManager.playOnce("sound/select.wav");
            SoundManager.stopAllMusic();
            returnCode = 1;
            this.isRunning = false;
        }

        if (!this.isPaused) {
            if (this.inputDelay.checkFinished() && !this.levelFinished) {

                // 1. Player Ship Logic
                for (int p = 0; p < GameState.NUM_PLAYERS; p++) {
                    Ship ship = this.ships[p];
                    if (ship == null || ship.isDestroyed()) continue;

                    boolean moveRight, moveLeft, fire;
                    if (p == 0) {
                        moveRight = inputManager.isP1RightPressed();
                        moveLeft = inputManager.isP1LeftPressed();
                        fire = inputManager.isP1ShootPressed();
                    } else {
                        moveRight = inputManager.isP2RightPressed();
                        moveLeft = inputManager.isP2LeftPressed();
                        fire = inputManager.isP2ShootPressed();
                    }

                    boolean isRightBorder = ship.getPositionX() + ship.getWidth() + ship.getSpeed() > this.width - 1;
                    boolean isLeftBorder = ship.getPositionX() - ship.getSpeed() < 1;

                    if (moveRight && !isRightBorder) ship.moveRight();
                    if (moveLeft && !isLeftBorder) ship.moveLeft();

                    if (fire && ship.shoot(this.bullets)) {
                        SoundManager.playOnce("sound/shoot.wav");
                        state.incBulletsShot(p);
                    }
                }

                // 2. Update player ships
                for (Ship s : this.ships)
                    if (s != null) s.update();

                // 3. Boss and Minion Logic
                if (this.boss != null && this.boss.getHp() > 0) {
                    this.boss.update(); // Boss moves, checks invuln, fires
                }


                if (this.minionFormation != null) {
                    this.minionFormation.update(); // Minions move
                    this.minionFormation.shoot(this.bullets); // Minions shoot
                }
            }

            // 4. Collision and Cleanup
            manageCollisions();
            cleanBullets();
            cleanItems();
            manageItemPickups();

            // 5. Item effects
            state.updateEffects();
            drawManager.setLastLife(state.getLivesRemaining() == 1);

            // 6. End Condition: Boss HP <= 0
            if (this.boss != null && this.boss.getHp() <= 0 && !this.levelFinished) {
                logger.info("Boss defeated!");

                // Recycle entities
                BulletPool.recycle(this.bullets);
                this.bullets.clear();
                ItemPool.recycle(items);
                this.items.clear();

                // Clear remaining minions
                if (this.minionFormation != null) {
                    for (EnemyShip minion : this.minionFormation) {
                        minion.destroy();
                    }
                }

                this.levelFinished = true;
                this.screenFinishedCooldown.reset();

                // Grant achievements
                if (!this.tookDamageThisLevel) {
                    achievementManager.unlock("Survivor");
                }
                achievementManager.unlock("Clear"); // "Clear" achievement
            }

            // 7. Screen transition
            if (this.levelFinished && this.screenFinishedCooldown.checkFinished()) {
                if (!achievementManager.hasPendingToasts()) {
                    this.isRunning = false;
                }
            }

            // 8. Achievement popups
            if (this.achievementManager != null) this.achievementManager.update();
        }

        // 9. Draw final frame
        draw();
    }

    /**
     * Draws the elements associated with the screen.
     */
    private void draw() {
        drawManager.initDrawing(this);

        drawManager.drawExplosions();
        drawManager.updateGameSpace(); // Background stars

        // Draw players
        for (Ship s : this.ships)
            if (s != null)
                drawManager.drawEntity(s, s.getPositionX(), s.getPositionY());

        // Draw Boss
        if (this.boss != null && this.boss.getHp() > 0)
            drawManager.drawEntity(this.boss, this.boss.getPositionX(), this.boss.getPositionY());

        // Draw Minions
        if (this.minionFormation != null)
            this.minionFormation.draw();

        // Draw Bullets
        for (Bullet bullet : this.bullets)
            drawManager.drawEntity(bullet, bullet.getPositionX(),
                    bullet.getPositionY());

        // Draw items
        for (Item item : this.items)
            drawManager.drawEntity(item, item.getPositionX(),
                    item.getPositionY());

        // Draw Top UI (Score, Lives, Coins)
        drawManager.drawScore(this, state.getScore());
        drawManager.drawLives(this, state.getLivesRemaining(), state.isCoop());
        drawManager.drawCoins(this, state.getCoins());
        drawManager.drawLevel(this, this.state.getLevel());
        drawManager.drawHorizontalLine(this, SEPARATION_LINE_HEIGHT - 1);

        // Draw Boss HP Bar
        if (this.boss != null) {
            // This method must be added to DrawManager.java
            drawManager.drawBossHPBar(this, this.boss.getHp(), this.boss.getMaxHp());
        }

        // Draw Minion count
        if (this.minionFormation != null)
            drawManager.drawShipCount(this, this.minionFormation.getShipCount());

        // Draw Countdown
        if (!this.inputDelay.checkFinished()) {
            int countdown = (int) ((INPUT_DELAY - (System.currentTimeMillis() - this.gameStartTime)) / 1000);
            drawManager.drawCountDown(this, this.state.getLevel(), countdown, false); // false for bonus life
            drawManager.drawHorizontalLine(this, this.height / 2 - this.height / 12);
            drawManager.drawHorizontalLine(this, this.height / 2 + this.height / 12);
        }

        // Draw Achievement Toasts
        drawManager.drawAchievementToasts(
                this,
                (this.achievementManager != null)
                        ? this.achievementManager.getActiveToasts()
                        : Collections.emptyList()
        );

        // Draw Pause Overlay
        if (this.isPaused) {
            drawManager.drawPauseOverlay(this);
        }

        drawManager.completeDrawing(this);
    }

    /**
     * Cleans bullets that go off screen.
     */
    private void cleanBullets() {
        Set<Bullet> recyclable = new HashSet<Bullet>();
        for (Bullet bullet : this.bullets) {
            bullet.update();
            if (bullet.getPositionY() < SEPARATION_LINE_HEIGHT
                    || bullet.getPositionY() > this.height)
                recyclable.add(bullet);
        }
        this.bullets.removeAll(recyclable);
        BulletPool.recycle(recyclable);
    }

    /**
     * Cleans items that go off screen.
     */
    private void cleanItems() {
        Set<Item> recyclableItems = new HashSet<Item>();
        for (Item item : this.items) {
            item.update();
            if (item.getPositionY() > this.height)
                recyclableItems.add(item);
        }
        this.items.removeAll(recyclableItems);
        ItemPool.recycle(recyclableItems);
    }

    /**
     * Manages pickups between player and items.
     */
    private void manageItemPickups() {
        Set<Item> collected = new HashSet<Item>();
        for (Item item : this.items) {
            for (Ship ship : this.ships) {
                if (ship == null) continue;
                if (checkCollision(item, ship) && !collected.contains(item)) {
                    collected.add(item);
                    this.logger.info("Player " + ship.getPlayerId() + " picked up item: " + item.getType());
                    SoundManager.playOnce("sound/hover.wav");
                    item.applyEffect(getGameState(), ship.getPlayerId());
                }
            }
        }
        this.items.removeAll(collected);
        ItemPool.recycle(collected);
    }

    /**
     * Manages collisions between bullets and entities.
     */
    private void manageCollisions() {
        Set<Bullet> recyclable = new HashSet<Bullet>();
        for (Bullet bullet : this.bullets) {
            if (bullet.getSpeed() > 0) {
                // Enemy Bullet vs Player
                for (int p = 0; p < GameState.NUM_PLAYERS; p++) {
                    Ship ship = this.ships[p];
                    if (ship != null && !ship.isDestroyed() && checkCollision(bullet, ship) && !this.levelFinished) {
                        recyclable.add(bullet);
                        drawManager.triggerExplosion(ship.getPositionX(), ship.getPositionY(), false, state.getLivesRemaining() == 1);
                        ship.addHit();
                        ship.destroy();
                        SoundManager.playOnce("sound/explosion.wav");
                        state.decLife(p);
                        this.tookDamageThisLevel = true;
                        drawManager.setLastLife(state.getLivesRemaining() == 1);
                        drawManager.setDeath(state.getLivesRemaining() == 0);
                        this.logger.info("Hit on player " + (p + 1));
                        break; // Bullet hits one player
                    }
                }
            } else {
                // Player Bullet
                final int ownerId = bullet.getOwnerPlayerId();
                final int pIdx = (ownerId == 2) ? 1 : 0;
                boolean hitRegistered = false;

                // Player bullet vs Minions
                if (this.minionFormation != null) {
                    for (EnemyShip enemyShip : this.minionFormation) {
                        if (!enemyShip.isDestroyed() && checkCollision(bullet, enemyShip)) {
                            recyclable.add(bullet);
                            enemyShip.hit();
                            hitRegistered = true;

                            if (enemyShip.isDestroyed()) {
                                int points = enemyShip.getPointValue();
                                state.addCoins(pIdx, enemyShip.getCoinValue());
                                drawManager.triggerExplosion(enemyShip.getPositionX(), enemyShip.getPositionY(), true, false); // Not final explosion
                                state.addScore(pIdx, points);
                                state.incShipsDestroyed(pIdx);

                                Item drop = engine.ItemManager.getInstance().obtainDrop(enemyShip);
                                if (drop != null) this.items.add(drop);

                                this.minionFormation.destroy(enemyShip);
                                SoundManager.playOnce("sound/invaderkilled.wav");
                            }
                            break; // Bullet hits one minion
                        }
                    }
                }

                if (hitRegistered) continue; // Bullet was used on a minion

                // Player bullet vs Boss
                if (this.boss != null && this.boss.getHp() > 0 && checkCollision(bullet, this.boss)) {
                    recyclable.add(bullet);

                    // boss.onHit(1)은 Boss.java 내부에서 스스로 무적 상태(쫄몹 생존)를 확인하므로
                    // 여기서 추가 확인이 필요 없습니다.
                    this.boss.onHit(1); // 1의 데미지를 줍니다.

                    // 보스가 이 총알에 의해 죽었는지 확인
                    if (this.boss.getHp() <= 0) {
                        // 보스 사망 시 폭발 이펙트 처리 (GameScreen 참고)
                        drawManager.triggerExplosion(this.boss.getPositionX() + this.boss.getWidth() / 2, this.boss.getPositionY() + this.boss.getHeight() / 2, true, true);
                        SoundManager.stop();
                        SoundManager.playOnce("sound/explosion.wav");
                    }
                }
            }
        }
        this.bullets.removeAll(recyclable);
        BulletPool.recycle(recyclable);
    }

    /**
     * Checks if two entities are colliding.
     *
     * @param a
     * First entity.
     * @param b
     * Second entity.
     * @return Result of the collision test.
     */
    private boolean checkCollision(final Entity a, final Entity b) {
        int centerAX = a.getPositionX() + a.getWidth() / 2;
        int centerAY = a.getPositionY() + a.getHeight() / 2;
        int centerBX = b.getPositionX() + b.getWidth() / 2;
        int centerBY = b.getPositionY() + b.getHeight() / 2;
        int maxDistanceX = a.getWidth() / 2 + b.getWidth() / 2;
        int maxDistanceY = a.getHeight() / 2 + b.getHeight() / 2;
        int distanceX = Math.abs(centerAX - centerBX);
        int distanceY = Math.abs(centerAY - centerBY);
        return distanceX < maxDistanceX && distanceY < maxDistanceY;
    }

    /**
     * Returns a GameState object representing the status of the game.
     *
     * @return Current game state.
     */
    public final GameState getGameState() {
        return this.state;
    }
}
