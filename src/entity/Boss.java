package entity;

import java.util.Objects;
import java.util.function.IntSupplier;

public class Boss {

    private final BulletEmitter emitter;
    private final IntSupplier minionAlive;
    private final Runnable spawnHP1;
    private final Runnable spawnHP2;
    private final Runnable clearShield;

    // === 상태 ===
    private int hp;
    private final int phase2Threshold;
    private int x = 0;
    private int y = 0;

    // 7-인자 생성자 (테스트와 일치)
    public Boss(int initialHp, int phase2Threshold,
                BulletEmitter emitter,
                IntSupplier minionAlive,
                Runnable spawnHP1,
                Runnable spawnHP2,
                Runnable clearShield) {

        this.hp = initialHp;
        this.phase2Threshold = phase2Threshold;
        this.emitter = emitter;
        this.minionAlive = minionAlive;
        this.spawnHP1 = spawnHP1;
        this.spawnHP2 = spawnHP2;
        this.clearShield = clearShield;

        // 선택: NPE 방지(스켈레톤이라도 안전하게)
        Objects.requireNonNull(this.spawnHP1).run();
    }

    // === 최소 시그니처만 제공 ===
    public void update() { /* intentionally empty for TDD */ }

    public void onHit(int damage) { /* intentionally empty for TDD */ }

    public int getHp() { return this.hp; }

    public int getX() { return this.x; }
    public int getY() { return this.y; }

    public BossPhase getPhase() { return BossPhase.P1; }

    public boolean isInvulnerable() { return true; }

    public void setFireEveryFramesP1(int frames) { /* no-op */ }

    public void setFireEveryFramesP2(int frames) { /* no-op */ }
}
