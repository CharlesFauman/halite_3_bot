package revamped;

import hlt.Game;
import hlt.Log;

public class GameState {

	Game game;
	public GameState() {
		game = new Game();
		game.ready("KnewBot");
		Log.log("Made KnewBot");
		
	}
	
	public void update() {
		game.updateFrame();
		
		// decide what ship is going to do
		
		// decide if spawn ship
		
	}
	
}
