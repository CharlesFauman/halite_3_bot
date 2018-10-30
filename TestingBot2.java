// This Java API uses camelCase instead of the snake_case as documented in the API docs.
//     Otherwise the names of methods are consistent.
import custom3.GameState2;

public class TestingBot2 {
    public static void main(final String[] args) {
        final GameState2 game_state = new GameState2();

        while (true) {
        	game_state.update();
        }
    }
}