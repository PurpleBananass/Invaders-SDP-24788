package engine;

public final class ReviveManager {

    private final GameState gameState;
    /** 각 레벨마다 이전에 부활한 적 있는지 여부를 확인하는 변수 */
    private boolean[] revived = {false, false, false, false, false, false};
    /** 부활에 필요한 비용 (변경 가능) */
    private static final int REVIVE_COST = 50;
    /** 현재 레벨 */
    private int level;

    public ReviveManager(GameState gameState) {
        this.gameState = gameState;
        this.level = gameState.getLevel();
    }

    /**
     * @return 현재 부활 가능한지 여부 */
    public boolean canRevive(int currentLevel) {
        return !revived[currentLevel-1];
    }

    /**
     * @return 부활 성공 시 true, 실패 시 false */
    public boolean tryRevive() {
        if (!canRevive(level)) {
            return false;
        }

        int coins = gameState.getCoins();

        if (coins >= REVIVE_COST) {
            gameState.spendCoins(0, REVIVE_COST);
            revived[level-1] = true;
            return true;
        }

        return false;
    }
}
