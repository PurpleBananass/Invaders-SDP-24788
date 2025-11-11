package screen;

import engine.GameState;
import screen.MapScreen ;

import org.junit.jupiter.api.BeforeEach ;
import org.junit.jupiter.api.Test ;
import org.junit.jupiter.api.DisplayName ;
import static org.junit.jupiter.api.Assertions.* ;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

public class MapScreenTest {

    private GameState mockGameState ;

    @BeforeEach
    void setUp() {
        mockGameState = mock(GameState.class) ;
    }

    @DisplayName("Exit 버튼 어쩌고")
    @Test
    void testExit() {
        // given : -

        // when : Exit 버튼을 누름

        // then : TitleScreen으로 화면이 전환되어야 함
    }

}
