package screen;

import engine.GameState;
import engine.SoundManager;

import java.awt.event.KeyEvent;

public class StoryScreen extends Screen {

    static final GameState gameState = null;
    static final GameScreen currentScreen = null;
    private final int currentLevel ;

    /**
     * Constructor, establishes the properties of the screen.
     *
     * @param width          Screen width.
     * @param height         Screen height.
     * @param fps           Frames per second, frame rate at which the game is run.
     * @param currentLevel  현재 스테이지 인덱스.
     */
    public StoryScreen(int width, int height, int fps, int currentLevel) {
        super(width, height, fps);
        this.currentLevel = currentLevel;
        this.returnCode = 1;

        SoundManager.playLoop("sound/menu_sound.wav");
    }

    @Override
    public final int run() {
        super.run();

        return this.returnCode;
    }

    @Override
    protected final void update() {
        super.update();

        if (this.inputDelay.checkFinished()) {
            if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
                this.returnCode = 5;  // go to PlayScreen
                this.isRunning = false;
            } else if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
                this.returnCode = 1;  // 메인 메뉴로
                this.isRunning = false;
            }
        }
        draw();
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawStory(this, this.currentLevel);
        drawManager.completeDrawing(this);
    }
}
