package custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import custom.ShipController.State;
import hlt.Command;
import hlt.Constants;
import hlt.Direction;
import hlt.EntityId;
import hlt.Game;
import hlt.GameMap;
import hlt.Log;
import hlt.MapCell;
import hlt.Player;
import hlt.Position;
import hlt.Ship;

public class GameState {
	
	Random rng;
	Game game;
	HashMap<EntityId, ShipController> ship_controllers;
	HashSet<Position> ship_positions;
	HashMap<Position, Integer> focus_positions;
	HashSet<Position> dropoffs;
    LinkedList<MutableInteger> dropoff_nums;
	int turn_halite;
	double min_halite;
	boolean spawned;
	
	public GameState(){
	    game = new Game();

        Log.log("Successfully created bot!");
        
        long rngSeed = System.nanoTime();
        rng = new Random(rngSeed);
        
        spawned = false;
        
        ship_controllers = new HashMap<>();
        ship_positions = new HashSet<>();
        dropoffs = new HashSet<>();
        focus_positions = new HashMap<>();
        dropoffs.add(game.me.shipyard.position);
        dropoff_nums = new LinkedList<>();
        
	    game.ready("MyJavaBot");
	}
	
	public Position getClosestDropoff(Position position) {
		Position closest = null;
		int distance = game.gameMap.height + game.gameMap.width;
		for (Position dropoffPos: dropoffs) {
			int dist = game.gameMap.calculateDistance(position, dropoffPos);
			if(dist < distance) {
				closest = dropoffPos;
				distance = dist;
			}
		}
		return closest;
	}
	
	public Position getHighestConcentration(Position from) {
		Position highest_concentration = null;
		double value = -1;
		GameMap game_map = game.gameMap;
		for(int i = 0; i < game_map.width; ++i){
			for(int j = 0; j < game_map.height; ++j){
				Position pos = new Position(i, j);
				int temp_distance = game_map.calculateDistance(from, game_map.normalize(new Position(pos.x + Hardcoded.FOCUS_SIZE/2, pos.y + Hardcoded.FOCUS_SIZE/2)));
				if(pos.equals(from)) {
					continue;
				}
				double temp = 0;
				int num_ships_me = 0;
				int num_ships_them = 0;
				LinkedList<Integer> num_dropoffs_them = new LinkedList<Integer>();
				int max_rad = Hardcoded.FOCUS_SIZE;
				for(int x = -Hardcoded.FOCUS_SIZE; x < Hardcoded.FOCUS_SIZE; ++ x) {
					for(int y = -Hardcoded.FOCUS_SIZE; y < Hardcoded.FOCUS_SIZE; ++y) {
						int rad = Math.abs(x) + Math.abs(y);
						if(rad > Hardcoded.FOCUS_SIZE) continue;
						Position current_pos = new Position(x + i,y + j);
						if(game_map.at(current_pos).structure != null && game_map.at(current_pos).structure.owner != game.me.id) {
							num_dropoffs_them.add(rad);
						}
						MapCell game_at = game_map.at(game_map.normalize(current_pos));
						double val;
						if(game_at.ship != null || focus_positions.containsKey(current_pos)) {
							if(focus_positions.containsKey(current_pos) || game_at.ship.owner == game.me.id) {
								num_ships_me += 1;
							}else{
								num_ships_them += 1;
							}
							val = 0;
						}else {
							 val = Math.max(game_map.at(game_map.normalize(current_pos)).halite-(min_halite), 0);
							 val *= Math.pow(0.8, rad);
						}
						temp += val;
					}
				}
				int distance_to_dropoff = game.gameMap.calculateDistance(pos, getClosestDropoff(pos));
				
				temp /= (max_rad*max_rad + (max_rad-1)*(max_rad-1));
				temp *= Math.pow(Hardcoded.DROPOFF_DISTANCE_DISCOUNT, (distance_to_dropoff + 1));
				temp *= Math.pow(Hardcoded.DISTANCE_DISCOUNT, (temp_distance+1));
				for(Iterator<Integer> itr = num_dropoffs_them.iterator(); itr.hasNext();) {
					temp*= (1 - Math.pow(.85, itr.next()));
				}
				if(temp > value) {
					value = temp;
					highest_concentration = game_map.normalize(new Position(pos.x + Hardcoded.FOCUS_SIZE/2, pos.y + Hardcoded.FOCUS_SIZE/2));
				}
			}
		}
		Log.log("found value: " + value);
		return highest_concentration;
	}
	
	public void update() {
        game.updateFrame();
        
        turn_halite = game.me.halite;
        
        for(Iterator<MutableInteger> itr = dropoff_nums.iterator(); itr.hasNext();) {
        	MutableInteger next = itr.next();
        	next.val += 1;
        	if(next.val >= 80) {
        		itr.remove();
        	}
        }
        
        focus_positions.clear();
        
        HashMap<EntityId, ShipController> new_ship_controllers = new HashMap<>();
        for(Ship ship : game.me.ships.values()) {
        	if(ship_controllers.containsKey(ship.id)) {
        		new_ship_controllers.put(ship.id, ship_controllers.get(ship.id));
        		new_ship_controllers.get(ship.id).update(ship);
        		ship_controllers.remove(ship.id);
        	}else {
        		new_ship_controllers.put(ship.id, new ShipController(ship, this));
        	}
        	ShipController controller = new_ship_controllers.get(ship.id);
        	if(controller.state == State.FOCUSING) {
        		if(focus_positions.containsKey(controller.focus_position)) {
        			focus_positions.put(controller.focus_position, focus_positions.get(controller.focus_position)+1);
        		}else {
        			focus_positions.put(controller.focus_position, 1);
        		}
        		
        	}
        }
        
        for(ShipController ship_controller : ship_controllers.values()) {
        	ship_controller.destroy();
        }
        
    	ship_controllers = new_ship_controllers;
        
        submitCommands();
	}
	
	private void submitCommands() {
        final ArrayList<Command> command_queue = new ArrayList<>();
        
       	double total_halite = 0;
		for(int i = 0; i < game.gameMap.width; ++i){
			for(int j = 0; j < game.gameMap.height; ++j){
				total_halite += game.gameMap.at(new Position(i, j)).halite;
			}
		}
		
		min_halite = total_halite / (game.gameMap.width*game.gameMap.height*Hardcoded.MIN_HALITE_MULTIPLY);
        
        ArrayList<ShipController> ships_controls = new ArrayList<ShipController>(ship_controllers.values());

        Collections.sort(ships_controls, new Comparator<ShipController>(){

            @Override
            public int compare(final ShipController o1, final ShipController o2){
                // let your comparator look up your car's color in the custom order
                return Integer.compare(o1.state.getValue(), o2.state.getValue());
            }
        });
        
        ship_positions.clear();
        
        if(spawned) {
        	ship_positions.add(game.me.shipyard.position);
        	spawned = false;
        }
        
        for (final ShipController ship_controller : ships_controls) {
        	command_queue.add(ship_controller.getCommand(this));
        }
        
        int buffer = 0;
        if(dropoff_nums.size() == 0 && game.turnNumber >= ((double)(2 + (game.players.size()/2))/4) * Hardcoded.DROPPOINT_SAVE_TURN_MULTIPLIER * (Hardcoded.DROPPOINT_SAVE_TURN_NUMERATOR/game.gameMap.width)) {
        	buffer = Constants.DROPOFF_COST;
        }
        
        if (
        	total_halite /((((double)(4+game.players.size())/3.0)*ship_controllers.size())+1) >= (1.6*Constants.SHIP_COST)/((double) (Math.max(Constants.MAX_TURNS - game.turnNumber, 200) / 200)) &&
            game.turnNumber <= Constants.MAX_TURNS * Hardcoded.LAST_SPAWN_TURN_MULTIPLIER &&
            ship_controllers.size() <= (Constants.MAX_TURNS - Hardcoded.MAX_BOT_NUMBER_SUBTRACTOR) * Hardcoded.MAX_BOT_NUMBER_MULTIPLIER &&
            turn_halite >= Constants.SHIP_COST  + buffer &&
            !ship_positions.contains(game.me.shipyard.position)
            )
        {
        	command_queue.add(game.me.shipyard.spawn());
        	//spawned = true;
        }

        game.endTurn(command_queue);
	}
}
