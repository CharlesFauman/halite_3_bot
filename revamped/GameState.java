package revamped;

import hlt.Game;
import hlt.Log;

public class GameState {

	Game game;
	public GameState() {
		game = new Game();
		game.ready("KnewBot");
		Log.log("Made KnewBot");
		
		// 1. determine value of halite on this current map
		
	}
	
	public void update() {
		game.updateFrame();
		
		int me_halite = game.me.halite;
		
		// decide what ship is going to do
		// 1. Go for either nearest halite or migrate to location with more halite
		// 2. If making a dropoff makes sense
		// 3. If already in a state of doing something
		
		// decide if spawn ship
		// 3 reason why NOT to spawn a ship
		// 1. Not enough halite on map
		// 2. We have enough ships
		// 3. Not enough turns left
		
	}
	
}
