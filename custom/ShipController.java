package custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
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
	int halite_gains_total;
	LinkedList<Integer> recent_gains;
	
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
		halite_gains_total = 0;
		recent_gains = new LinkedList<>();
		
		setDirectionsOrder();
	}
	
	public double scoreMove(Position pos, Direction direction, GameState game_state, Integer halite, HashMap<Position, Integer> new_halites, Integer depth) {
		
		int limit = (int) (Hardcoded.LOW_HALITE_MULTIPLIER * Constants.MAX_HALITE);
		
		Position new_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(direction));
		boolean inspired = false;
		int near_enemies = 0;
		if(Constants.INSPIRATION_ENABLED) {
			int rad = Constants.INSPIRATION_RADIUS;
			for(int x = 0; x < rad*2; ++ x) {
				for(int y = 0; y < rad*2; ++y) {
					if(x + y > rad) continue;
					Ship target_ship = game_state.game.gameMap.at(game_state.game.gameMap.normalize(new Position(new_pos.x + x - rad,new_pos.y + y - rad))).ship;
					if(target_ship != null && target_ship.owner != game_state.game.me.id) {
						near_enemies += 1;
					}
				}
			}
		}
		if(near_enemies >= Constants.INSPIRATION_SHIP_COUNT) {
			inspired = true;
		}
		
		int halite_after = ship.halite;
		if(direction.equals(Direction.STILL)) {
			Integer halite_at_position = game_state.game.gameMap.at(pos).halite;
			if(halite_at_position <= limit) {
				return 0;
			}
			if(new_halites.containsKey(pos)) {
				halite_at_position = new_halites.get(pos);
			}
			Integer change_at_position;
			if(inspired) {
				change_at_position = halite_at_position / Constants.INSPIRED_EXTRACT_RATIO;
				halite_after += change_at_position + change_at_position*Constants.INSPIRED_BONUS_MULTIPLIER;
			}else {
				change_at_position = halite_at_position / Constants.EXTRACT_RATIO;
				halite_after += change_at_position;
			}
			new_halites.put(pos, halite_at_position - change_at_position);
		}else {
			if(inspired) {
				halite_after -= game_state.game.gameMap.at(ship.position).halite / Constants.MOVE_COST_RATIO;
			}else {
				halite_after -= game_state.game.gameMap.at(ship.position).halite / Constants.INSPIRED_MOVE_COST_RATIO;
			}
		}
		halite_after = Math.min(halite_after, Constants.MAX_HALITE);
		
		double halite_next = 0;
		int enemies_next_to = 0;
		if(depth > 0 ) {
			for(Direction new_direction : directions_order.keySet()) {
				halite_next = Math.max(halite_next, scoreMove(new_pos, new_direction, game_state, halite_after, new HashMap<Position, Integer>(new_halites), depth-1));
				Position next_pos = game_state.game.gameMap.normalize(new_pos.directionalOffset(new_direction));
				Ship target_ship = game_state.game.gameMap.at(next_pos).ship;
				if(target_ship != null && target_ship.owner != game_state.game.me.id) {
					enemies_next_to += 1;
				}
			}
		}

		return (halite_after + halite_next * Hardcoded.FUTURE_VALUE_MULTIPLIER) / (2*(1 + enemies_next_to));
		
	}
	
	public boolean maybe_dropoff(GameState game_state, Position final_pos) {
		int distance = game_state.game.gameMap.calculateDistance(final_pos, game_state.getClosestDropoff(final_pos));
		if(distance >= Hardcoded.MIN_DROPPOINT_DISTANCE) {
			
			double near_halite = 0;
			int rad = Hardcoded.DROPPOINT_SPAWN_RADIUS;
			for(int x = -rad; x <= rad; ++ x) {
				for(int y = -rad; y <= rad; ++y) {
					if(Math.abs(x) + Math.abs(y) > rad) continue;
					near_halite += game_state.game.gameMap.at(game_state.game.gameMap.normalize(new Position(final_pos.x + x,final_pos.y + y))).halite;
				}
			}
			
			near_halite /= (rad*rad + (rad-1)*(rad-1));
			
			if(near_halite >= Hardcoded.DROPPOINT_SPAWN_HALITE) {
				if(game_state.turn_halite >= Constants.DROPOFF_COST) {
					game_state.dropoffs.add(ship.position);
					game_state.turn_halite -= Constants.DROPOFF_COST;
					return true;
				}
			}
		}
		return false;
	}

	public Command getCommand(GameState game_state) {
		Log.log("id: " + ship.id + ", position: " + ship.position.toString() + ", halite: " + ship.halite + ", state: " + state);
		Direction best_direction;
		
		if(state != State.GATHERING) {
			recent_gains.clear();
			halite_gains_total = 0;
		}
		
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
		
		if(final_halite >= 0.96 * Constants.MAX_HALITE) {
			Log.log("Returning with " + final_halite);
			if(maybe_dropoff(game_state, final_pos)) {
				return ship.makeDropoff();
			}
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
			
			if(maybe_dropoff(game_state, final_pos)) {
				return ship.makeDropoff();
			}
			
		}
		
		if(state == State.GATHERING) {
			recent_gains.addLast(final_halite - ship.halite);
			halite_gains_total += final_halite - ship.halite;
			if(recent_gains.size() >= Hardcoded.BAD_GATHERING_STREAK_SIZE) {
				int removing = recent_gains.pollFirst();
				halite_gains_total -= removing;
				if(halite_gains_total <= Hardcoded.BAD_GATHERING_STREAK_SIZE * Hardcoded.BAD_GATHERING_STREAK_AMOUNT_PER) {
					state = State.FOCUSING;
					focus_position = game_state.getHighestConcentration(final_pos);
					Log.log("bad gathering; new focus on : " + focus_position);
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
