package screen;

import engine.GameState ;

public class MapScreen {

    public static GameState gameState ;

    /**
     * @return Exit 버튼 클릭 여부를 반환합니다.
     * */
    public boolean isClickedExit() {
        return true ;
    }

    public void changeScreen() {
        if ( isClickedExit() ) gameState
    }
}
