package screen;

import engine.GameState ;
import engine.SoundManager;

import java.awt.event.KeyEvent;

public class MapScreen extends Screen {

    static final GameState gameState = null;
    static final GameScreen currentScreen = null;

    /**
     * Constructor, establishes the properties of the screen.
     *
     * @param width          Screen width.
     * @param height         Screen height.
     * @param fps           Frames per second, frame rate at which the game is run.
     * @param currentLevel  현재 스테이지 인덱스.
     */
    public MapScreen(int width, int height, int fps, int currentLevel) {
        super(width, height, fps);
        this.currentLevel = currentLevel;
        this.returnCode = 1;

        SoundManager.playLoop("sound/menu_sound.wav");
    }

    /** 현재스테이지 인덱스 */
    private final int currentLevel ;

    @Override
    public final int run() {
        super.run();

        return this.returnCode;
    }

    @Override
    protected final void update() {
        super.update();

        draw();

        // 키 입력
        if (this.inputDelay.checkFinished()) {
            if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
                this.returnCode = 5;  // go to PlayScreen
                this.isRunning = false;
            } else if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
                this.returnCode = 1;  // 메인 메뉴로
                this.isRunning = false;
            }
        }
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawMap(this, this.currentLevel);
        drawManager.drawMapBackground();
        drawManager.completeDrawing(this);
    }

}