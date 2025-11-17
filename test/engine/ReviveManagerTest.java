package engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviveManagerTest {

    private GameState mockGameState;
    private ReviveManager reviveManager;
    private int currentLevel;

    @BeforeEach
    void setUp() {
        mockGameState = mock(GameState.class);
        when(mockGameState.getLevel()).thenReturn(1);

        reviveManager = new ReviveManager(mockGameState);
        currentLevel = mockGameState.getLevel();
    }

    @Test
    @DisplayName("초기 상태: 부활한 적 없음")
    void initState() {
        // given : -

        // when : 부활 가능한지 확인
        boolean canRevive = reviveManager.canRevive(currentLevel);

        // then : 초기에는 부활이 가능해야 함
        assertTrue(canRevive);
    }

    @Test
    @DisplayName("코인이 충분하면 revive 성공")
    void reviveWithEnoughCoins() {
        // given : 가지고 있는 코인이 충분하다고 가정
        when(mockGameState.getCoins()).thenReturn(100);

        // when : 부활 시도
        boolean result = reviveManager.tryRevive();

        // then : 부활에 성공해야 함
        assertTrue(result);
        verify(mockGameState).spendCoins(eq(0), anyInt());
    }

    @Test
    @DisplayName("코인이 부족하면 revive 실패")
    void reviveWithNotEnoughCoins() {
        // given : 가지고 있는 코인이 부족하다고 가정
        when(mockGameState.getCoins()).thenReturn(10);

        // when : 부활 시도
        boolean result = reviveManager.tryRevive();

        // then : 부활에 실패해야 함
        assertFalse(result);
        verify(mockGameState, never()).spendCoins(eq(0), anyInt());
    }

    @Test
    @DisplayName("이미 부활한 레벨에서는 부활 실패")
    void reviveAlreadyRevivedLevel() {
        // given : 코인은 충분하고, 부활을 이미 한 번 했다고 가정
        when(mockGameState.getCoins()).thenReturn(100);
        assertTrue(reviveManager.tryRevive());

        // when : 부활 전적이 있는 상태에서 또 부활 가능한지 시도
        boolean result = reviveManager.tryRevive();

        // then : 부활했던 적이 있다면 다음 부활부터는 실패해야 함
        assertFalse(result);
    }
}
