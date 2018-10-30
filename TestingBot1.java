// This Java API uses camelCase instead of the snake_case as documented in the API docs.
//     Otherwise the names of methods are consistent.
import custom2.GameState;

public class TestingBot1 {
    public static void main(final String[] args) {
        final GameState game_state = new GameState();

        while (true) {
        	game_state.update();
        }
    }
}