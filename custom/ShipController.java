package custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import hlt.Command;
import hlt.Constants;
import hlt.Direction;
import hlt.Entity;
import hlt.GameMap;
import hlt.Log;
import hlt.MapCell;
import hlt.Position;
import hlt.Ship;

public class ShipController {

	Ship ship;
	State state;
	Position focus_position;
	HashMap<Direction, Integer> directions_order;
	int halite_gains_total;
	LinkedList<Integer> recent_gains;

	// State enum with priorities
	enum State {
		STUCK(0), FOCUSING(3), RETURNING(1), GATHERING(2);

		private final int value;

		private State(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	/**
	 * Initialize local variable with all directions
	 */
	private void setDirectionsOrder() {
		ArrayList<Direction> tmp = new ArrayList<>(Direction.ALL_CARDINALS);
		Collections.shuffle(tmp);
		tmp.add(Direction.STILL);
		directions_order = new HashMap<>();
		for (int i = 0; i < tmp.size(); ++i) {
			directions_order.put(tmp.get(i), i);
		}
		Log.log(directions_order.toString());
	}

	// TODO: This seems unnecessary
	public void update(Ship ship) {
		this.ship = ship;
	}

	// TODO: ???
	public void destroy() {
		//
	}

	/**
	 * Constructor
	 * 
	 * @param ship
	 *            Ship this controller is in reference to
	 * @param game_state
	 *            The current gamestate of gameMap
	 */
	public ShipController(Ship ship, GameState game_state) {
		Log.log("new ship with id: " + ship.id);
		update(ship);
		focus_position = game_state.getHighestConcentration(ship.position);
		state = State.FOCUSING;
		halite_gains_total = 0;
		recent_gains = new LinkedList<>();

		setDirectionsOrder();
	}

	/**
	 * This is used to score moves
	 * 
	 * @param pos
	 *            Current Position
	 * @param direction
	 *            Direction to next position
	 * @param game_state
	 *            Passing in global variable
	 * @param halite
	 *            TODO
	 * @return The score of a move
	 */
	public double scoreMove(Position pos, Direction direction, GameState game_state, Integer halite) {
		int limit = (int) (game_state.min_halite);

		Position new_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(direction));
		if (game_state.alreadyAMove(new_pos)) {
			Log.log("Move " + direction + " runs into another ship, do not consider this direction...");
			return 0;
		}
		boolean inspired = false;
		int near_enemies = 0;

		// This determines if inspiration bonus is activated or not...
		if (Constants.INSPIRATION_ENABLED) {
			int rad = Constants.INSPIRATION_RADIUS;
			for (int x = -rad; x <= rad; ++x) {
				for (int y = -rad; y <= rad; ++y) {
					if (Math.abs(x) + Math.abs(y) > rad)
						continue;
					Ship target_ship = game_state.game.gameMap
							.at(game_state.game.gameMap.normalize(new Position(new_pos.x + x, new_pos.y + y))).ship;
					if (target_ship != null && target_ship.owner != game_state.game.me.id) {
						near_enemies += 1;
					}
				}
			}
		}
		if (near_enemies >= Constants.INSPIRATION_SHIP_COUNT) {
			inspired = true;
		}

		// SCORING MOVE BASE ON HALITE COLLECTED
		int halite_after = ship.halite;
		// TODO: this seems to not be used, can we remove it?
		int halite_next = game_state.game.gameMap.at(new_pos).halite;
		if (direction.equals(Direction.STILL)) {
			Integer halite_at_position = game_state.game.gameMap.at(pos).halite;
			if (halite_at_position <= limit) {
				halite_at_position = 0;
			}
			Integer change_at_position;
			if (inspired) {
				change_at_position = halite_at_position / Constants.INSPIRED_EXTRACT_RATIO;
				halite_after += change_at_position + change_at_position * Constants.INSPIRED_BONUS_MULTIPLIER;
			} else {
				change_at_position = halite_at_position / Constants.EXTRACT_RATIO;
				halite_after += change_at_position;
			}
			halite_next -= change_at_position;
		} else {
			if (inspired) {
				halite_after -= game_state.game.gameMap.at(ship.position).halite / Constants.INSPIRED_MOVE_COST_RATIO;
			} else {
				halite_after -= game_state.game.gameMap.at(ship.position).halite / Constants.MOVE_COST_RATIO;
			}
		}
		halite_after = Math.max(halite_after, 0);
		halite_after = Math.min(halite_after, Constants.MAX_HALITE);

		Log.log("HALITE_AFTER:" + halite_after);

		// TODO: What is this doing
		int rad = 6;
		if (direction == Direction.STILL) {
			if (game_state.game.gameMap.at(pos).halite >= limit) {
				return halite_after + 2 * limit;
			}
			return halite_after;
		}

		// TODO: what is this doing
		Position current = new_pos;
		Direction right = direction.turnRight();
		Direction left = right.invertDirection();
		int num_ships_me = 0;
		int num_ships_them = 0;
		LinkedList<Integer> num_dropoffs_them = new LinkedList<Integer>();
		double worth = 0;
		GameMap game_map = game_state.game.gameMap;
		for (int dir = 0; dir <= rad; ++dir) {
			Position current_pos = current.directionalOffset(right, (rad - dir));
			for (int side = -(rad - dir); side <= (rad - dir); ++side) {
				int dist = dir + Math.abs(side);

				if (game_map.at(current_pos).structure != null
						&& game_map.at(current_pos).structure.owner != game_state.game.me.id) {
					num_dropoffs_them.add(dist);
				}
				MapCell game_at = game_map.at(game_map.normalize(current_pos));
				double val;
				if (game_at.ship != null) {
					if (game_at.ship.owner == game_state.game.me.id) {
						num_ships_me += 1;
					} else {
						num_ships_them += 1;
					}
					val = 0;
				} else {
					val = Math.max(game_map.at(game_map.normalize(current_pos)).halite - (game_state.min_halite), 0);
					val *= Math.pow(0.8, dist);
				}
				worth += val;

				current_pos = current_pos.directionalOffset(left);
			}
			current = current.directionalOffset(direction);
		}

		// This is logic to run our ship into another ship (TODO: is this
		// right?)
		if (num_ships_me < num_ships_them) {
			worth *= Math.pow(.9, (num_ships_them - num_ships_me));
		} else {
			worth *= Math.pow(1.04, num_ships_them);
		}

		Ship other_ship = game_state.game.gameMap.at(new_pos).ship;

		// TODO: please comment this logic
		if (other_ship != null && other_ship.owner != game_state.game.me.id) {
			Entity structure = game_state.game.gameMap.at(new_pos).structure;
			if (structure == null || structure.owner == game_state.game.me.id) {
				double halite_diff = ship.halite - 2 * other_ship.halite;
				if (num_ships_me > num_ships_them) {
					worth += halite_diff / 20;
				} else {
					worth -= ship.halite / 20;
				}
			}
		}

		// TODO: What is worth
		worth = worth / (30 * (rad ^ 2));

		Log.log("WORTH: " + worth);

		// TODO: I definitely think the A-star type algorithm is best/optimal
		// either in addition or instead of this
		// lets compare this vs A-star/depth-based

		return halite_after + worth;

	}

	/**
	 * TODO: what is this doing, please comment
	 * 
	 * @param game_state
	 *            Global variable
	 * @param final_pos
	 *            TODO: ?
	 * @return TODO: ?
	 */
	public boolean check_dropoff(GameState game_state, Position final_pos) {
		if (game_state.game.gameMap.at(final_pos).hasStructure() || game_state.dropoff_nums.size() >= 2)
			return false;
		int distance = game_state.game.gameMap.calculateDistance(final_pos, game_state.getClosestDropoff(final_pos));
		if (distance >= Hardcoded.MIN_DROPPOINT_DISTANCE) {

			double near_halite = 0;
			int rad = Hardcoded.DROPPOINT_SPAWN_RADIUS;
			for (int x = -rad; x <= rad; ++x) {
				for (int y = -rad; y <= rad; ++y) {
					if (Math.abs(x) + Math.abs(y) > rad)
						continue;
					int pos_halite = game_state.game.gameMap.at(
							game_state.game.gameMap.normalize(new Position(final_pos.x + x, final_pos.y + y))).halite;
					near_halite += Math.max(pos_halite - game_state.min_halite, 0);
				}
			}

			near_halite /= (rad * rad + (rad - 1) * (rad - 1));

			if (near_halite >= Hardcoded.DROPPOINT_SPAWN_HALITE) {
				if (game_state.turn_halite >= Constants.DROPOFF_COST) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * TODO: EVEN MORE OF A ????, comment please
	 * 
	 * @param game_state
	 *            TODO
	 * @param pos
	 *            TODO
	 * @return TODO
	 */
	public Position maybe_dropoff(GameState game_state, Position pos) {
		if (game_state.game.gameMap.at(pos).hasStructure() || game_state.dropoff_nums.size() >= 2)
			return null;
		double best_score = 0;
		Position best_placement = null;
		int max_rad = 6;
		for (int x1 = -max_rad; x1 < max_rad; ++x1) {
			for (int y1 = -max_rad; y1 < max_rad; ++y1) {
				int outer_rad = Math.abs(x1) + Math.abs(y1);
				if (outer_rad > max_rad)
					continue;
				Position final_pos = new Position(pos.x + x1, pos.y + y1);
				int distance = game_state.game.gameMap.calculateDistance(final_pos,
						game_state.getClosestDropoff(final_pos));
				if (distance >= Hardcoded.MIN_DROPPOINT_DISTANCE) {

					double near_halite = 0;
					int rad = Hardcoded.DROPPOINT_SPAWN_RADIUS;
					for (int x = -rad; x <= rad; ++x) {
						for (int y = -rad; y <= rad; ++y) {
							if (Math.abs(x) + Math.abs(y) > rad)
								continue;
							int pos_halite = game_state.game.gameMap.at(game_state.game.gameMap
									.normalize(new Position(final_pos.x + x, final_pos.y + y))).halite;
							near_halite += Math.max(pos_halite - game_state.min_halite, 0);
						}
					}

					near_halite /= (rad * rad + (rad - 1) * (rad - 1));

					if (near_halite < best_score)
						continue;

					if (near_halite >= Hardcoded.DROPPOINT_SPAWN_HALITE) {
						if (game_state.turn_halite >= Constants.DROPOFF_COST) {
							best_score = near_halite;
							best_placement = final_pos;
						}
					}
				}
				//
			}
		}
		return best_placement;
	}

	/**
	 * Getting the 'optimal' command
	 */
	public Command getCommand(GameState game_state) {
		Log.log("id: " + ship.id + ", position: " + ship.position.toString() + ", halite: " + ship.halite + ", state: "
				+ state);
		Direction best_direction;

		// TODO: this logic will be able to be optimized in the future
		// especially if we are changing stuff around

		if (state != State.GATHERING) {
			recent_gains.clear();
			halite_gains_total = 0;
		}

		if (state == State.STUCK) {
			best_direction = Direction.STILL;
		} else {
			HashMap<Direction, Double> direction_scores = new HashMap<>();

			for (Direction direction : directions_order.keySet()) {
				Position new_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(direction));
				int distance = game_state.game.gameMap.calculateDistance(new_pos,
						game_state.getClosestDropoff(new_pos));

				if (distance + game_state.game.turnNumber + Hardcoded.RETURN_TURN_BUFFER >= Constants.MAX_TURNS) {
					state = State.RETURNING;
				}

				double score = 0;

				switch (state) {
				case RETURNING:
					score = -1 * distance;
					if (direction == Direction.STILL) {
						score -= 2;
					}
					break;
				case FOCUSING:
					score = -200 * game_state.game.gameMap.calculateDistance(new_pos, focus_position);
					// TODO idk about this logic
					if (ship.halite < Constants.MAX_HALITE * .8) {
						score += scoreMove(new_pos, direction, game_state, ship.halite);
					}
					break;
				case GATHERING:
					score = scoreMove(new_pos, direction, game_state, ship.halite);
					break;
				default:
					break;
				}

				// Making sure ships don't collide
				if (game_state.game.gameMap.at(new_pos).ship != null && state != State.GATHERING
						&& state != State.FOCUSING) {
					if (!game_state.game.gameMap.at(new_pos).ship.owner.equals(game_state.game.me.id)
							&& distance > Hardcoded.ENEMY_NO_COLLIDE_DISTANCE) {
						continue;
					}
				}

				if (!game_state.ship_positions.contains(new_pos)) {
					direction_scores.put(direction, score);
				}
			}

			// figuring out the based direction based on scores
			if (direction_scores.size() == 0) {
				best_direction = Direction.STILL;
			} else {
				Map.Entry<Direction, Double> max_entry = null;

				for (Map.Entry<Direction, Double> entry : direction_scores.entrySet()) {
					if (max_entry == null || entry.getValue().compareTo(max_entry.getValue()) > 0
							|| (entry.getValue().compareTo(max_entry.getValue()) == 0 && directions_order
									.get(entry.getKey()) < directions_order.get(max_entry.getKey()))) {
						max_entry = entry;
					}
				}

				best_direction = max_entry.getKey();
			}
		}

		// TODO: SOME LOGIC (ALEX WILL LOOK AT THIS LATERs)
		Position final_pos = game_state.game.gameMap.normalize(ship.position.directionalOffset(best_direction));
		int final_halite = ship.halite;
		if (best_direction.equals(Direction.STILL)) {
			final_halite += game_state.game.gameMap.at(ship.position).halite / Constants.EXTRACT_RATIO;
		} else {
			final_halite -= game_state.game.gameMap.at(ship.position).halite / Constants.MOVE_COST_RATIO;
		}
		final_halite = Math.min(final_halite, 1000);

		// determines how to unstuck something
		int position_halite = game_state.game.gameMap.at(final_pos).halite;
		if (state == State.STUCK) {
			position_halite -= position_halite / Constants.EXTRACT_RATIO;
			if (focus_position == null) {
				state = State.GATHERING;
			} else {
				state = State.FOCUSING;
			}
		}

		// If this we are stuck (losing too much halite for a move) - could
		// factor this into scoring?
		if (final_halite < position_halite / Constants.MOVE_COST_RATIO) {
			Log.log("Stucking, " + final_halite + ", pos_h:" + position_halite);
			state = State.STUCK;
		}

		// RETURNING --> FOCUSING/GATHERINGs
		if (state == State.RETURNING && game_state.dropoffs.contains(final_pos)) {

			state = State.FOCUSING;
			focus_position = game_state.getHighestConcentration(final_pos);
			Log.log("focusing on : " + focus_position);

			// TODO: I THINK THIS IS WRONG
			state = State.GATHERING;
		}

		// If we want to make a dropoff
		if (state == State.FOCUSING
				&& (final_pos.equals(focus_position) || final_halite >= (double) Constants.MAX_HALITE / 8)) {
			focus_position = null;
			state = State.GATHERING;
			// good job! you abstracted some of this - now abstract all of it!
			if (!game_state.game.gameMap.at(ship.position).hasStructure() && check_dropoff(game_state, final_pos)) {
				game_state.dropoffs.add(ship.position);
				game_state.dropoff_nums.add(new MutableInteger(0));
				game_state.turn_halite -= Constants.DROPOFF_COST;
				return ship.makeDropoff();
			}

		}

		// Please comment this logic - also we want to decrease
		// BAD_GATHERING_STREAK_AMOUNT_PER (from 115 -> 80/85)
		if (state == State.GATHERING) {
			recent_gains.addLast(final_halite - ship.halite);
			halite_gains_total += Math.max(final_halite - ship.halite, 0);
			if (recent_gains.size() > Hardcoded.BAD_GATHERING_STREAK_SIZE) {
				int removing = recent_gains.pollFirst();
				halite_gains_total -= removing;
				if (halite_gains_total <= Hardcoded.BAD_GATHERING_STREAK_SIZE
						* Hardcoded.BAD_GATHERING_STREAK_AMOUNT_PER) {
					state = State.FOCUSING;
					focus_position = game_state.getHighestConcentration(final_pos);
					Log.log("bad gathering; new focus on : " + focus_position);
				}
			}
		}

		// we want to return if we have over a certain amount (changing from 900
		// to 915)
		if (final_halite >= 915) {
			Log.log("Returning with " + final_halite);
			Position target = maybe_dropoff(game_state, final_pos);
			if (target != null) {
				state = State.FOCUSING;
				focus_position = target;
				Log.log("Actually, dropping with " + target);
			} else {
				state = State.RETURNING;
			}
		}

		Log.log("new pos" + final_pos + "new state: " + state + ", final_halite: " + final_halite);

		if (!(game_state.dropoffs.contains(final_pos) && game_state.game.turnNumber + 30 >= Constants.MAX_TURNS)) {
			game_state.ship_positions.add(final_pos);
		}

		return ship.move(best_direction);
	}
}
