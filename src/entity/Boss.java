package entity;

import java.awt.Color;
import java.util.function.IntSupplier;
import engine.DrawManager.SpriteType;

public class Boss extends Entity {

    private final int maxHp = 10;
    private int hp = 10;
    private BossPhase phase = BossPhase.P1;
    /** 매 update마다 쫄몹 수로 다시 계산되는 무적 여부. */
    private boolean invulnerable = true;

    // ===== 이동/발사 파라미터 =====
    // 한 번 이동할 때의 "배수" 느낌으로 사용 (실제 이동량 = X_SPEED * speedP? )
    /** P1 이동 배율. */
    private int speedP1_pxPerFrame = 1;
    /** P2 이동 배율. */
    private int speedP2_pxPerFrame = 1;

    // 프레임 카운터 방식: N프레임마다 발사
    /** P1 페이즈에서 N프레임마다 발사. */
    private int fireEveryFramesP1 = 200;
    /** P2 페이즈에서 N프레임마다 발사. */
    private int fireEveryFramesP2 = 100;
    /** 발사를 위한 프레임 카운터. */
    private int frameCounter = 0;

    private static final int BULLET_SPEED = 4;

    // ===== 협력자(테스트/실게임에서 주입) =====
    private final BulletEmitter emitter;
    private final IntSupplier minionAlive; // 쫄몹 생존 수
    private final Runnable spawnHP1Group;  // B/C 5기 스폰 훅
    private final Runnable spawnHP2Group;  // A 5기 스폰 훅
    private final Runnable clearShield;    // 기존 방패 제거 훅
    private final Runnable onPhase2Start;

    private static final int BOSS_WIDTH = 50;
    private static final int BOSS_HEIGHT = 30;

    // 화면 정보 & 이동 관련
    private final int screenWidth;
    private final int marginX = 10;

    // EnemyShipFormation 비슷한 이동용 필드
    private enum Direction { RIGHT, LEFT }

    private Direction currentDirection = Direction.RIGHT;
    /** 이동 타이밍 제어용 카운터 (프레임 단위). */
    private int movementInterval = 0;
    /** P1 페이즈에서 movementInterval이 이 값에 도달할 때마다 한 번 이동. */
    private int movementSpeedP1 = 50;
    /** P2 페이즈에서 movementInterval이 이 값에 도달할 때마다 한 번 이동. */
    private int movementSpeedP2 = 80;
    /** 한 번 이동할 때 기본 이동 거리(픽셀). 여기에 speedP? 배율이 곱해짐. */
    private final int X_SPEED = 8;


    // ===== 생성 =====
    public Boss(
            int x, int y, int screenWidth,
            BulletEmitter emitter,
            IntSupplier minionAlive,
            Runnable spawnHP1Group,
            Runnable spawnHP2Group,
            Runnable clearShield,
            Runnable onPhase2Start
    ) {
        super(x, y, BOSS_WIDTH * 2, BOSS_HEIGHT * 2, Color.RED);
        this.emitter = emitter;
        this.minionAlive = minionAlive;
        this.spawnHP1Group = spawnHP1Group;
        this.spawnHP2Group = spawnHP2Group;
        this.clearShield = clearShield;
        this.onPhase2Start = onPhase2Start;
        this.spriteType = SpriteType.Boss;
        this.screenWidth = screenWidth;

        // 시작: P1 방패(HP1) 5기
        if (this.spawnHP1Group != null) {
            this.spawnHP1Group.run();
        }
        // 실제 반영은 첫 update에서 쫄몹 생존 수로 보정
        this.invulnerable = true;
    }


    public void update() {
        moveHorizontally(); // EnemyShipFormation 스타일 좌우 이동

        // 무적 = (쫄몹 생존 수 > 0)
        if (this.minionAlive != null) {
            this.invulnerable = (this.minionAlive.getAsInt() > 0);
        }

        // 발사(무적 여부와 무관)
        this.frameCounter++;
        if (this.phase == BossPhase.P1) {
            if (this.frameCounter % this.fireEveryFramesP1 == 0) {
                fireNWay(3);
            }
        } else {
            if (this.frameCounter % this.fireEveryFramesP2 == 0) {
                fireNWay(5);
            }
        }
    }

    // ===== 피격 처리 =====
    public void onHit(final int dmg) {
        if (this.invulnerable) {
            return;
        }
        this.hp -= dmg;

        // HP가 절반 이하로 떨어지는 시점에 P2 진입
        if (this.hp <= this.maxHp / 2 && this.phase == BossPhase.P1) {
            enterP2();
        }

        if (this.hp <= 0) {
            // 죽음 처리 필요시 추가 (폭발 이펙트 등)
            this.hp = 0;
        }
    }

    private void enterP2() {
        this.phase = BossPhase.P2;
        if (this.clearShield != null) {
            this.clearShield.run();
        }
        if (this.spawnHP2Group != null) {
            this.spawnHP2Group.run(); // HP2 5기
        }
        if (this.onPhase2Start != null) {
            this.onPhase2Start.run();
        }
        // invulnerable은 다음 update에서 쫄몹 생존 수로 자동 반영
        // 발사 주기는 frameCounter 유지(주기만 달라짐)
    }

    /**
     * N-way 탄막 발사. 중앙 기준으로 대칭 각도 배열 사용.
     *
     * @param ways 발사 개수 (3, 5, 7 등)
     */
    private void fireNWay(final int ways) {
        // emitter가 없으면 아무것도 하지 않음 (NPE 방지)
        if (this.emitter == null) {
            return;
        }

        int spawnX = this.positionX + this.getWidth() / 2;
        int spawnY = this.positionY + this.getHeight();

        double[] angles;

        if (ways == 3) {
            angles = new double[]{-15.0, 0.0, 15.0};
        } else if (ways == 5) {
            angles = new double[]{-30.0, -15.0, 0.0, 15.0, 30.0};
        } else if (ways == 4) {
            angles = new double[]{-21.0, -7.0, 7.0, 21.0};
        } else if (ways == 7) {
            angles = new double[]{-30.0, -20.0, -10.0, 0.0, 10.0, 20.0, 30.0};
        } else {
            angles = new double[]{0.0};
        }

        for (double angleDeg : angles) {
            // 삼각함수 계산을 위해 각도를 라디안으로 변환합니다.
            // (여기서는 0도를 위쪽 대신 아래쪽 방향에 맞추기 위해 +90도 보정)
            double angleRad = Math.toRadians(angleDeg + 90.0);

            // 속도 벡터(vx, vy) 계산
            int vx = (int) (Math.cos(angleRad) * BULLET_SPEED);
            int vy = (int) (Math.sin(angleRad) * BULLET_SPEED);

            // BossScreen의 emitter가 이 vx, vy를 받아 실제 Bullet 생성 처리
            this.emitter.fire(spawnX, spawnY, vx, vy);
        }
    }

    public void setPosition(final int x, final int y) {
        this.positionX = x;
        this.positionY = y;
    }

    /**
     * EnemyShipFormation 느낌으로 "툭툭" 이동하는 좌우 이동 로직.
     */
    private void moveHorizontally() {
        int currentMovementSpeed = (this.phase == BossPhase.P1 ? this.movementSpeedP1 : this.movementSpeedP2);

        // 매 프레임 카운트만 올리다가
        this.movementInterval++;
        if (this.movementInterval < currentMovementSpeed) {
            // 아직 움직일 타이밍 아님 → 그대로 정지
            return;
        }

        // 움직일 타이밍이 됐으면 카운터 리셋
        this.movementInterval = 0;

        // 현재 페이즈에 따른 속도 배율 적용
        int speed = (this.phase == BossPhase.P1 ? this.speedP1_pxPerFrame : this.speedP2_pxPerFrame);

        // 지금 방향 기준으로 한 번에 움직일 거리 계산
        int movementX = (this.currentDirection == Direction.RIGHT ? this.X_SPEED * speed : -this.X_SPEED * speed);

        int candidateX = this.positionX + movementX;

        // 화면 경계 체크
        boolean isAtLeftSide = candidateX <= this.marginX;
        boolean isAtRightSide = candidateX + this.getWidth() >= this.screenWidth - this.marginX;

        if (isAtLeftSide) {
            // 왼쪽 벽에 닿으면 오른쪽으로 방향 전환
            this.currentDirection = Direction.RIGHT;
            candidateX = this.marginX;  // 벽 안으로 딱 붙여줌
        } else if (isAtRightSide) {
            // 오른쪽 벽에 닿으면 왼쪽으로 방향 전환
            this.currentDirection = Direction.LEFT;
            candidateX = this.screenWidth - this.marginX - this.getWidth();
        }

        // 최종 위치 적용
        this.positionX = candidateX;
    }

    // ===== 테스트/튜닝용 getter & 설정치 =====
    public int getHp() {
        return this.hp;
    }

    public int getMaxHp() {
        return this.maxHp;
    }

    public BossPhase getPhase() {
        return this.phase;
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    // 테스트 편의를 위한 즉시 튜닝용 세터
    public void setFireEveryFramesP1(final int n) {
        this.fireEveryFramesP1 = Math.max(1, n);
    }

    public void setFireEveryFramesP2(final int n) {
        this.fireEveryFramesP2 = Math.max(1, n);
    }

    public void setSpeedP1_pxPerFrame(final int v) {
        this.speedP1_pxPerFrame = v;
    }

    public void setSpeedP2_pxPerFrame(final int v) {
        this.speedP2_pxPerFrame = v;
    }
}
