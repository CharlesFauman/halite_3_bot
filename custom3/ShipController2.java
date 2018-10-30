package custom3;

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

public class ShipController2 {
	
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
	}
	
	public void update(Ship ship) {
		this.ship = ship;
	}
	
	public ShipController2(Ship ship){
		update(ship);
		state = State.GATHERING;
		halite_gains_total = 0;
		recent_gains = new LinkedList<>();
		
		setDirectionsOrder();
	}
	
	/*
		Think about the A-Star algorithm. Definitely need to factor in inspiration at some point...

		Is it worth it to have inspiration activated for you when it could also activate it for your opponent as well?
	*/
	public double scoreMove(Position pos, Direction direction, GameState2 game_state, Integer halite, HashMap<Position, Integer> new_halites, Integer depth) {
		
		int limit = 12;
		Position new_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(direction));
		Ship target_ship = game_state.game.gameMap.at(game_state.game.gameMap.normalize(new_pos)).ship;

		/*
		Checking for inspiration
		*/
		boolean inspired = false;
		int near_enemies = 0;
		if (Constants.INSPIRATION_ENABLED) {
			int rad = Constants.INSPIRATION_RADIUS;
			for (int i = -rad; i < rad; i++) {
				for (int j = -rad; j < rad; j++) {
					if (i + j > rad) continue;
					Ship testing = game_state.game.gameMap.at(game_state.game.gameMap.normalize(new Position(new_pos.x + i,new_pos.y + j))).ship;
					if (testing != null && testing.owner != game_state.game.me.id) near_enemies++;
				}
			}
		}

		if (near_enemies >= Constants.INSPIRATION_SHIP_COUNT) inspired = true;

		int halite_after = ship.halite;
		if(direction.equals(Direction.STILL)) {
			Integer halite_at_position = game_state.game.gameMap.at(pos).halite;
			if(halite_at_position <= limit) {
				return 0;
			}
			if(new_halites.containsKey(pos)) {
				halite_at_position = new_halites.get(pos);
			}
			int change_at_position = 0;
			if (inspired) {
				change_at_position = halite_at_position / Constants.INSPIRED_EXTRACT_RATIO;
				halite_after += change_at_position * (1 + Constants.INSPIRED_BONUS_MULTIPLIER);
			} else {
				change_at_position = halite_at_position / Constants.EXTRACT_RATIO;
				halite_after += change_at_position;
			}
			new_halites.put(pos, halite_at_position - change_at_position);
		}else {
			if (inspired) {
				halite_after -= game_state.game.gameMap.at(ship.position).halite / Constants.INSPIRED_MOVE_COST_RATIO;
			} else {
				halite_after -= game_state.game.gameMap.at(ship.position).halite / Constants.MOVE_COST_RATIO;
			}
			if (target_ship != null) halite_after *= .5;
		}
		double halite_next = 0;
		if(depth > 0 ) {
			for(Direction new_direction : directions_order.keySet()) {
				halite_next = Math.max(halite_next, scoreMove(new_pos, new_direction, game_state, halite_after, new HashMap<Position, Integer>(new_halites), depth-1));

			}
		}

		return halite_after + halite_next * .53;
		
	}

	public Command getCommand(GameState2 game_state) {
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
				
				if(distance + game_state.game.turnNumber + 5 >= Constants.MAX_TURNS) {
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
					score = scoreMove(new_pos, direction, game_state, ship.halite, new HashMap<Position, Integer>(), 3);
					break;
				default:
					break;
				}
				
				if(game_state.game.gameMap.at(new_pos).ship != null) {
					if(!game_state.game.gameMap.at(new_pos).ship.owner.equals(game_state.game.me.id) && distance > (game_state.game.gameMap.width <= 40 ? 4: 6)) {
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
		
		// if(final_halite >= (0.94 - .15 * Math.max(1, (game_state.game.turnNumber) / (Constants.MAX_TURNS/2))) * Constants.MAX_HALITE) state = State.RETURNING;
		if(final_halite >= .92 * Constants.MAX_HALITE) state = State.RETURNING;
		if(final_halite < position_halite / Constants.MOVE_COST_RATIO) state = State.STUCK;
		
		if(state == State.RETURNING && game_state.dropoffs.contains(final_pos)) {
			state = State.FOCUSING;
			focus_position = game_state.getHighestConcentration(final_pos);
		}
		
		// WEIRD BUG: https://api.2018.halite.io/v1/api/user/1872/match/1103151/error_log
		//			  https://api.2018.halite.io/v1/api/user/1872/match/1104844/error_log
		
		if(state == State.FOCUSING && final_pos.equals(focus_position)){
			focus_position = null;
			state = State.GATHERING;
			int distance = game_state.game.gameMap.calculateDistance(final_pos, game_state.getClosestDropoff(final_pos));
			if(distance >= (game_state.game.gameMap.width <= 48 ? 17: 16)) {
				int total_near = 0;
				for(ShipController2 ship_control : game_state.ship_controllers.values()) {
					int s_dist = game_state.game.gameMap.calculateDistance(final_pos, ship_control.ship.position);
					if(s_dist < 5) {
						total_near += 1;
					}
				}
				
				int near_halite = 0;
				int rad = 7;
				for(int x = 0; x < rad; ++ x) {
					for(int y = 0; y < rad; ++y) {
						near_halite += game_state.game.gameMap.at(game_state.game.gameMap.normalize(new Position(final_pos.x + x - rad/2,final_pos.y + y - rad/2))).halite;
					}
				}
				
				// if((total_near >= 5 || (2 >= total_near && near_halite / (rad*rad) >= (game_state.game.gameMap.width <= 48 ? 270 : 250)))
				// 	&& (near_halite / (rad*rad) > (game_state.game.gameMap.width <= 48 ? 375: 350) || game_state.game.turnNumber < Constants.MAX_TURNS * 4.0 / 5.0)
				// 	&& (near_halite / (rad*rad) > (game_state.game.gameMap.width <= 48 ? 375: 350) || game_state.total_halite >= (game_state.game.gameMap.width <= 48 ? 1175: 1075) * game_state.total_ships)
				// 	) {
				if((total_near >= 5 || (total_near >= 2 && near_halite / (rad*rad) >= 255)) && game_state.game.turnNumber < Constants.MAX_TURNS * 5.0 / 6.0
					// && (near_halite / (rad*rad) > (game_state.game.gameMap.width < 64 ? 0: 350) || game_state.total_halite >= (game_state.game.gameMap.width < 64 ? 0: 1075) * game_state.total_ships)
					) {
					if(game_state.game.me.halite > Constants.DROPOFF_COST - ship.halite && game_state.dropoff_timer == 0 && !game_state.makingDropoff && !game_state.game.gameMap.at(ship.position).hasStructure()) {
						game_state.makingDropoff = true;
						game_state.dropoffs.add(ship.position);
						game_state.dropoff_timer = 35;
						return ship.makeDropoff();
					}
				}
			}
		}
		
		if(state == State.GATHERING) {
			recent_gains.addLast(final_halite - ship.halite);
			halite_gains_total += final_halite - ship.halite;
			if(recent_gains.size() >= 4) {
				int removing = recent_gains.pollFirst();
				halite_gains_total -= removing;
				if(halite_gains_total <= 335) {
					state = State.FOCUSING;
					focus_position = game_state.getHighestConcentration(final_pos);
				}
			}
		}
		
		if(!(game_state.dropoffs.contains(final_pos) &&
				game_state.game.turnNumber + 30 >= Constants.MAX_TURNS))
			game_state.ship_positions.add(final_pos);
		
		return ship.move(best_direction);
	}
}
