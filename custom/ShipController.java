package custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import hlt.Command;
import hlt.Constants;
import hlt.Direction;
import hlt.Log;
import hlt.Position;
import hlt.Ship;

public class ShipController {
	
	Ship ship;
	State state;
	Position focus_position;
	HashMap<Direction, Integer> directions_order;
	
	enum State 
	{ 
	    STUCK(0), FOCUSING(1), RETURNING(2), GATHERING(3); 
		
	    private final int value;
	    private State(int value) {
	        this.value = value;
	    }

	    public int getValue() {
	        return value;
	    }
	} 
	
	private void setDirectionsOrder() {
		ArrayList<Direction> tmp = new ArrayList<>(Direction.ALL_CARDINALS);
		Collections.shuffle(tmp);
		tmp.add(Direction.STILL);
		directions_order = new HashMap<>();
		for(int i = 0; i < tmp.size(); ++i) {
			directions_order.put(tmp.get(i), i);
		}
		Log.log(directions_order.toString());
	}
	
	public void update(Ship ship) {
		this.ship = ship;
	}
	
	public ShipController(Ship ship){
		Log.log("new ship with id: " + ship.id);
		update(ship);
		state = State.GATHERING;
		setDirectionsOrder();
	}
	
	public double scoreMove(Position pos, Direction direction, GameState game_state, Integer halite, HashMap<Position, Integer> new_halites, Integer depth) {
		
		int limit = (int) (Hardcoded.LOW_HALITE_MULTIPLIER * Constants.MAX_HALITE);
		
		int halite_after = ship.halite;
		if(direction.equals(Direction.STILL)) {
			Integer halite_at_position = game_state.game.gameMap.at(pos).halite;
			if(halite_at_position <= limit) {
				return 0;
			}
			if(new_halites.containsKey(pos)) {
				halite_at_position = new_halites.get(pos);
			}
			Integer change_at_position = halite_at_position / Constants.EXTRACT_RATIO;
			halite_after += change_at_position;
			new_halites.put(pos, halite_at_position - change_at_position);
		}else {
			halite_after -= game_state.game.gameMap.at(ship.position).halite / Constants.MOVE_COST_RATIO;
		}
		halite_after = Math.min(halite_after, Constants.MAX_HALITE);
		
		Position new_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(direction));
		
		double halite_next = 0;
		if(depth > 0 ) {
			for(Direction new_direction : directions_order.keySet()) {
				halite_next = Math.max(halite_next, scoreMove(new_pos, new_direction, game_state, halite_after, new HashMap<Position, Integer>(new_halites), depth-1));
			}
		}

		return halite_after + halite_next * Hardcoded.FUTURE_VALUE_MULTIPLIER;
		
	}

	public Command getCommand(GameState game_state) {
		Log.log("id: " + ship.id + ", position: " + ship.position.toString() + ", halite: " + ship.halite + ", state: " + state);
		Direction best_direction;
		if(state == State.STUCK) {
			best_direction = Direction.STILL;
		}else {
			HashMap<Direction, Double> direction_scores = new HashMap<>();
			
			for(Direction direction : directions_order.keySet()) {
				Position new_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(direction));
				int distance = game_state.game.gameMap.calculateDistance(new_pos, game_state.getClosestDropoff(new_pos));
				
				if(distance + game_state.game.turnNumber + Hardcoded.RETURN_TURN_BUFFER >= Constants.MAX_TURNS) {
					state = State.RETURNING;
				}
				
				double score = 0;
				
				switch(state) {
				case RETURNING:
					score = -1*distance;
					break;
				case FOCUSING:
					score = -1*game_state.game.gameMap.calculateDistance(new_pos, focus_position);
					break;
				case GATHERING:
					score = scoreMove(new_pos, direction, game_state, ship.halite, new HashMap<Position, Integer>(), Hardcoded.LOOKAHEAD);
					break;
				default:
					break;
				}
				
				if(game_state.game.gameMap.at(new_pos).ship != null) {
					if(!game_state.game.gameMap.at(new_pos).ship.owner.equals(game_state.game.me.id) && distance > Hardcoded.ENEMY_NO_COLLIDE_DISTANCE) {
						continue;
					}
				}
				
				if(!game_state.ship_positions.contains(new_pos)) {
					direction_scores.put(direction, score);
				}
			}
			
			if(direction_scores.size() == 0) {
				best_direction = Direction.STILL;
			}else {
				Map.Entry<Direction, Double> max_entry = null;

				for (Map.Entry<Direction, Double> entry : direction_scores.entrySet())
				{
				    if (max_entry == null || 
				    		entry.getValue().compareTo(max_entry.getValue()) > 0
				    		|| (entry.getValue().compareTo(max_entry.getValue()) == 0 &&
				    			directions_order.get(entry.getKey()) < directions_order.get(max_entry.getKey())
				    		)
				    		)
				    {
				    	max_entry = entry;
				    }
				}
				
				best_direction = max_entry.getKey();
			}
		}
		
		Position final_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(best_direction));
		int final_halite = ship.halite;
		if(best_direction.equals(Direction.STILL)) {
			final_halite += game_state.game.gameMap.at(ship.position).halite / Constants.EXTRACT_RATIO;
		}else {
			final_halite -= game_state.game.gameMap.at(ship.position).halite / Constants.MOVE_COST_RATIO;
		}
		final_halite = Math.min(final_halite, 1000);
		
		int position_halite = game_state.game.gameMap.at(final_pos).halite;
		if(state == State.STUCK) {
			position_halite -= position_halite / Constants.EXTRACT_RATIO;
			if(focus_position == null){
				state = State.GATHERING;
			}else {
				state = State.FOCUSING;
			}
		}
		
		if(final_halite >= 0.9 * Constants.MAX_HALITE) {
			Log.log("Returning with " + final_halite);
			state = State.RETURNING;
		}
		
		if(final_halite < position_halite / Constants.MOVE_COST_RATIO) {
			Log.log("Stucking, " + final_halite + ", pos_h:" + position_halite);
			state = State.STUCK;
		}
		
		if(state == State.RETURNING && game_state.dropoffs.contains(final_pos)) {
			state = State.FOCUSING;
			focus_position = game_state.getHighestConcentration(final_pos);
			Log.log("focusing on : " + focus_position);
		}
		
		if(state == State.FOCUSING && final_pos.equals(focus_position)){
			focus_position = null;
			state = State.GATHERING;
			int distance = game_state.game.gameMap.calculateDistance(final_pos, game_state.getClosestDropoff(final_pos));
			if(distance >= Hardcoded.MIN_DROPPOINT_DISTANCE) {
				int total_near = 0;
				for(ShipController ship_control : game_state.ship_controllers.values()) {
					int s_dist = game_state.game.gameMap.calculateDistance(final_pos, ship_control.ship.position);
					if(s_dist  <= Hardcoded.DROPPOINT_SPAWN_RADIUS) {
						total_near += 1;
					}
				}
				
				if(total_near >= Hardcoded.DROPPOINT_SPAWN_TARGET) {
					if(game_state.game.me.halite >= Constants.DROPOFF_COST) {
						game_state.dropoffs.add(ship.position);
						return ship.makeDropoff();
					}
				}
			}
		}
		
		Log.log("new pos" + final_pos + "new state: " + state + ", final_halite: " + final_halite);
		
		if(game_state.dropoffs.contains(final_pos) &&
				game_state.game.turnNumber + 30 >= Constants.MAX_TURNS) {
			//
		}else {
			game_state.ship_positions.add(final_pos);
		}
		
		return ship.move(best_direction);
	}
}
