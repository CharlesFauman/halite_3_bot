package custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import hlt.Command;
import hlt.Constants;
import hlt.Direction;
import hlt.EntityId;
import hlt.Game;
import hlt.GameMap;
import hlt.Log;
import hlt.Player;
import hlt.Position;
import hlt.Ship;

public class GameState {
	
	Random rng;
	Game game;
	Integer search_dist;
	HashMap<EntityId, ShipController> ship_controllers;
	HashSet<Position> ship_positions;
	HashSet<Position> dropoffs;
	
	public GameState(){
	    game = new Game();

        Log.log("Successfully created bot!");
        
        long rngSeed = System.nanoTime();
        rng = new Random(rngSeed);
        search_dist = Hardcoded.START_SEARCH_DIST;
        
        ship_controllers = new HashMap<>();
        ship_positions = new HashSet<>();
        dropoffs = new HashSet<>();
        dropoffs.add(game.me.shipyard.position);
        
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
				if(pos.equals(from) || search_dist < temp_distance) {
					continue;
				}
				int temp = 0;
				for(int x = 0; x < Hardcoded.FOCUS_SIZE; ++ x) {
					for(int y = 0; y < Hardcoded.FOCUS_SIZE; ++y) {
						temp += game_map.at(game_map.normalize(new Position(x + i,y + j))).halite;
					}
				}
				temp /= (temp_distance+1)*Hardcoded.DISTANCE_DISCOUNT_MULTIPLIER;
				if(temp > value) {
					value = temp;
					highest_concentration = game_map.normalize(new Position(pos.x + Hardcoded.FOCUS_SIZE/2, pos.y + Hardcoded.FOCUS_SIZE/2));
				}
			}
		}
		return highest_concentration;
	}
	
	public void update() {
        game.updateFrame();
        
        if(game.turnNumber != 0 && game.turnNumber % Hardcoded.SEARCH_DIST_MOD == 0) {
        	search_dist += Hardcoded.SEARCH_DIST_INCREMENT;
        }

        HashMap<EntityId, ShipController> new_ship_controllers = new HashMap<>();
        for(Ship ship : game.me.ships.values()) {
        	if(ship_controllers.containsKey(ship.id)) {
        		new_ship_controllers.put(ship.id, ship_controllers.get(ship.id));
        		new_ship_controllers.get(ship.id).update(ship);
        	}else {
        		new_ship_controllers.put(ship.id, new ShipController(ship));
        	}
        }
        
    	ship_controllers = new_ship_controllers;
        
        submitCommands();
	}
	
	private void submitCommands() {
        final ArrayList<Command> command_queue = new ArrayList<>();
        
        ArrayList<ShipController> ships_controls = new ArrayList<ShipController>(ship_controllers.values());

        Collections.sort(ships_controls, new Comparator<ShipController>(){

            @Override
            public int compare(final ShipController o1, final ShipController o2){
                // let your comparator look up your car's color in the custom order
                return Integer.compare(o1.state.getValue(), o2.state.getValue());
            }
        });
        
        ship_positions.clear();
        
        for (final ShipController ship_controller : ships_controls) {
        	command_queue.add(ship_controller.getCommand(this));
        }
        
        int buffer = 0;
        if(game.turnNumber >= Hardcoded.DROPPOINT_SAVE_TURN_MULTIPLIER * (Hardcoded.DROPPOINT_SAVE_TURN_NUMERATOR/game.gameMap.width)) {
        	buffer = Constants.DROPOFF_COST;
        }
        
        int total_ships = 0;
       	for(Player player : game.players) {
       		total_ships += player.ships.size();
       	}
       	
       	int total_halite = 0;
		for(int i = 0; i < game.gameMap.width; ++i){
			for(int j = 0; j < game.gameMap.height; ++j){
				total_halite += game.gameMap.at(new Position(i, j)).halite;
			}
		}

        if (
        	(total_halite / (total_ships+1)) >= (1.5*Constants.SHIP_COST)/((double) (Math.max(Constants.MAX_TURNS - game.turnNumber, 100) / 100)) &&
            game.turnNumber <= Constants.MAX_TURNS * Hardcoded.LAST_SPAWN_TURN_MULTIPLIER &&
            ship_controllers.size() <= (Constants.MAX_TURNS - Hardcoded.MAX_BOT_NUMBER_SUBTRACTOR) * Hardcoded.MAX_BOT_NUMBER_MULTIPLIER &&
            game.me.halite >= Constants.SHIP_COST  + buffer &&
            !ship_positions.contains(game.me.shipyard.position)
            )
        {
        	command_queue.add(game.me.shipyard.spawn());
        }

        game.endTurn(command_queue);
	}
}
