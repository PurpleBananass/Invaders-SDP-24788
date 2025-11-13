package entity;

import java.awt.*;
import java.util.function.IntSupplier;
import engine.DrawManager.SpriteType;

public class Boss extends Entity {

    private final int maxHp = 10;
    private int hp = 10;
    private BossPhase phase = BossPhase.P1;
    private boolean invulnerable = true; // 매 update에서 쫄몹 수로 재계산

    // ===== 이동/발사 파라미터 =====
    // 한 번 이동할 때의 "배수" 느낌으로 사용 (실제 이동량 = X_SPEED * speedP? )
    private int speedP1_pxPerFrame = 1;   // P1 이동 배율
    private int speedP2_pxPerFrame = 1;   // P2 이동 배율

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

    private static final int BOSS_WIDTH = 50;
    private static final int BOSS_HEIGHT = 30;

    // 화면 정보 & 이동 관련
    private final int screenWidth;
    private final int marginX = 10;

    // EnemyShipFormation 비슷한 이동용 필드
    private enum Direction { RIGHT, LEFT }

    private Direction currentDirection = Direction.RIGHT;
    private int movementInterval = 0;   // 매 프레임 +1
    private int movementSpeed = 50;     // 클수록 더 느리게 (EnemyShipFormation 의 movementSpeed 느낌)
    private final int X_SPEED = 2;      // 한번 움직일 때 기본 몇 픽셀 이동할지 (여기에 speedP? 배율 곱함)

    // ===== 생성 =====
    public Boss(
            int x, int y, int screenWidth,
            BulletEmitter emitter,
            IntSupplier minionAlive,
            Runnable spawnHP1Group,
            Runnable spawnHP2Group,
            Runnable clearShield
    ) {
        super(x, y, BOSS_WIDTH * 2, BOSS_HEIGHT * 2, Color.RED);
        this.emitter = emitter;
        this.minionAlive = minionAlive;
        this.spawnHP1Group = spawnHP1Group;
        this.spawnHP2Group = spawnHP2Group;
        this.clearShield = clearShield;
        this.spriteType = SpriteType.Boss;
        this.screenWidth = screenWidth;

        // 시작: P1 방패(HP1) 5기
        if (spawnHP1Group != null) this.spawnHP1Group.run();
        this.invulnerable = true; // 실제 반영은 첫 update에서 생존 수로 보정
    }

    // ===== 메인 루프: 프레임 단위 업데이트 =====
    public void update() {

        moveHorizontally(); // EnemyShipFormation 스타일 좌우 이동

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

    public void setPosition(int x, int y) {
        this.positionX = x; // Boss 클래스의 위치 필드를 업데이트
        this.positionY = y;
    }

    /**
     * EnemyShipFormation 느낌으로 "툭툭" 이동하는 좌우 이동 로직.
     */
    private void moveHorizontally() {
        // 매 프레임 카운트만 올리다가
        movementInterval++;
        if (movementInterval < movementSpeed) {
            // 아직 움직일 타이밍 아님 → 그대로 정지
            return;
        }
        // 움직일 타이밍이 됐으면 카운터 리셋
        movementInterval = 0;

        // 현재 페이즈에 따른 속도 배율 적용
        int speed = (phase == BossPhase.P1 ? speedP1_pxPerFrame : speedP2_pxPerFrame);

        // 지금 방향 기준으로 한 번에 움직일 거리 계산
        int movementX = (currentDirection == Direction.RIGHT ? X_SPEED * speed : -X_SPEED * speed);

        int candidateX = this.positionX + movementX;

        // 화면 경계 체크
        boolean isAtLeftSide  = candidateX <= marginX;
        boolean isAtRightSide = candidateX + this.getWidth() >= screenWidth - marginX;

        if (isAtLeftSide) {
            // 왼쪽 벽에 닿으면 오른쪽으로 방향 전환
            currentDirection = Direction.RIGHT;
            candidateX = marginX;  // 벽 안으로 딱 붙여줌
        } else if (isAtRightSide) {
            // 오른쪽 벽에 닿으면 왼쪽으로 방향 전환
            currentDirection = Direction.LEFT;
            candidateX = screenWidth - marginX - this.getWidth();
        }

        // 최종 위치 적용
        this.positionX = candidateX;
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
