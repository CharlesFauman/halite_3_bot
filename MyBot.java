
// This Java API uses camelCase instead of the snake_case as documented in the API docs.
//     Otherwise the names of methods are consistent.
import revamped.GameState;

public class MyBot {
	public static void main(final String[] args) {
		GameState game = new GameState();

		while (true)
			game.update();

	}
}