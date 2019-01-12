package revamped;

import java.util.HashMap;

import hlt.EntityId;
import hlt.Game;
import hlt.Log;
import hlt.Position;
import hlt.Ship;

public class GameState {
	
	enum State {
		 RETURNING(0), GATHERING(1);

		private final int value;

		private State(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	Game game;
	HashMap<EntityId, State> ship_states;
	
	
	public GameState() {
		game = new Game();
		
		// initialize variables
		
		ship_states = new HashMap<EntityId, State>();
		
		//
		
		
		
		
		Log.log("Made KnewBot");
		game.ready("KnewBot");
	}
	
	
	public double getValue(Ship ship, Position position) {
		
		// distance to position
		// amount halite in ship
		// distance to dropoff
		// value of position
		
		return 0;
	}
	
	public void update() {
		game.updateFrame();
		
		int me_halite = game.me.halite;
		
		
		for(Ship ship : game.me.ships.values()) {
			State current_state = State.GATHERING;
			if(ship_states.containsKey(ship.id)) {
				current_state = ship_states.get(ship.id);
			}
			
			switch(current_state) {
			case GATHERING:
				
				break;
			case RETURNING:
				
				break;
			default:
				System.err.println("NOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO");
				break;
			}
			
		}
		
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
