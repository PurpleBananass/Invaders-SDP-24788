package entity;

import java.awt.*;
import java.util.function.IntSupplier;
import java.util.function.IntSupplier;


public class Boss extends Entity {

    private final int maxHp = 10;
    private int hp = 10;
    private BossPhase phase = BossPhase.P1;
    private boolean invulnerable = true; // 매 update에서 쫄몹 수로 재계산

    // ===== 이동/발사 파라미터 =====
    private int speedP1_pxPerFrame = 2;   // 프레임당 픽셀 (간단화)
    private int speedP2_pxPerFrame = 3;

    // 프레임 카운터 방식: N프레임마다 발사
    private int fireEveryFramesP1 = 36;   // 60fps 기준 ~0.6s
    private int fireEveryFramesP2 = 24;   // ~0.4s
    private int frameCounter = 0;

    private int spreadDxP1 = 16; // 3갈래 시 좌/중/우 간격
    private int spreadDxP2 = 14; // 5갈래 시 좌/중/우 대칭 간격
    private int bulletVy = +220; // 수직 하강 속도(엔진에 맞춰 어댑터에서 처리해도 됨)

    // ===== 협력자(테스트/실게임에서 주입) =====
    private final BulletEmitter emitter;
    private final IntSupplier minionAlive; // 쫄몹 생존 수
    private final Runnable spawnHP1Group;  // B/C 5기 스폰 훅
    private final Runnable spawnHP2Group;  // A 5기 스폰 훅
    private final Runnable clearShield;    // 기존 방패 제거 훅

    // ===== 생성 =====
    public Boss(
            int x, int y,
            BulletEmitter emitter,
            IntSupplier minionAlive,
            Runnable spawnHP1Group,
            Runnable spawnHP2Group,
            Runnable clearShield
    ) {
        super(x, y, 80, 60, Color.RED);
        this.emitter = emitter;
        this.minionAlive = minionAlive;
        this.spawnHP1Group = spawnHP1Group;
        this.spawnHP2Group = spawnHP2Group;
        this.clearShield = clearShield;

        // 시작: P1 방패(HP1) 5기
        if (spawnHP1Group != null) this.spawnHP1Group.run();
        this.invulnerable = true; // 실제 반영은 첫 update에서 생존 수로 보정
    }

    // ===== 메인 루프: 프레임 단위 업데이트 =====
    public void update() {
        // 이동
        this.positionY += (phase == BossPhase.P1 ? speedP1_pxPerFrame : speedP2_pxPerFrame);

        // 무적 = (쫄몹 생존 수 > 0)
        if (minionAlive != null) {
            this.invulnerable = (minionAlive.getAsInt() > 0);
        }

        // 발사(무적 여부와 무관)
        frameCounter++;
        if (phase == BossPhase.P1) {
            if (frameCounter % fireEveryFramesP1 == 0) fireNWay(3, spreadDxP1);
        } else {
            if (frameCounter % fireEveryFramesP2 == 0) fireNWay(5, spreadDxP2);
        }
    }

    // ===== 피격 처리 =====
    public void onHit(int dmg) {
        if (invulnerable) return;
        hp -= dmg;
        if (hp <= maxHp / 2 && phase == BossPhase.P1) { // HP가 5 되는 순간
            enterP2();
        }
        if (hp <= 0) {
            // 죽음 처리 필요시 추가
        }
    }

    private void enterP2() {
        phase = BossPhase.P2;
        if (clearShield != null) clearShield.run();
        if (spawnHP2Group != null) spawnHP2Group.run(); // HP2 5기
        // invulnerable은 다음 update에서 쫄몹 생존 수로 자동 반영
        // 발사주기는 frameCounter 유지(주기만 달라짐)
    }

    private void fireNWay(int ways, int dx) {
        int mid = ways / 2;
        int spawnY = this.positionY;
        for (int i = 0; i < ways; i++) {
            int offset = (i - mid) * dx;
            emitter.fire(this.positionX + offset, spawnY, 0, bulletVy);
        }
    }

    // ===== 테스트/튜닝용 getter & 설정치 =====
    public int getHp() { return hp; }
    public int getMaxHp() {return this.maxHp;}
    public BossPhase getPhase() { return phase; }
    public boolean isInvulnerable() { return invulnerable; }

    // 테스트 편의를 위한 즉시 튜닝용 세터
    public void setFireEveryFramesP1(int n) { this.fireEveryFramesP1 = Math.max(1, n); }
    public void setFireEveryFramesP2(int n) { this.fireEveryFramesP2 = Math.max(1, n); }
    public void setSpeedP1_pxPerFrame(int v) { this.speedP1_pxPerFrame = v; }
    public void setSpeedP2_pxPerFrame(int v) { this.speedP2_pxPerFrame = v; }
    public void setSpreadDxP1(int v) { this.spreadDxP1 = v; }
    public void setSpreadDxP2(int v) { this.spreadDxP2 = v; }
    public void setBulletVy(int v) { this.bulletVy = v; }
}
