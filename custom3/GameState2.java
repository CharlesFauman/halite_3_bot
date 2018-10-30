package custom3;

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

public class GameState2 {
	
	Game game;
	Integer search_dist;
	HashMap<EntityId, ShipController2> ship_controllers;
	HashSet<Position> ship_positions;
	HashSet<Position> dropoffs;
	boolean makingDropoff;
	int total_halite;
	int total_ships;
	int buffer;
	int dropoff_timer;
	
	public GameState2(){
	    game = new Game();
        search_dist = (game.gameMap.width == 64 ? 9 : 12);
        ship_controllers = new HashMap<>();
        ship_positions = new HashSet<>();
        dropoffs = new HashSet<>();
        dropoffs.add(game.me.shipyard.position);
	    game.ready("Rank40ishBot");
	    total_halite = 0;
	    total_ships = 0;
	    dropoff_timer = 0;
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

	private int botsWithFocusPosition(Position position) {
		int counter = 0;
		for (ShipController2 s: ship_controllers.values()) {
			if (game.gameMap.calculateDistance(s.ship.position, position) <= 4) counter++;
		}
		return counter;
	}

	private int determineRadiusGivenTurn() {
		double turn = 1.0*game.turnNumber;
		int start = 4;
		int middle = 5;
		int end = 2;
		double turnMax = (Constants.MAX_TURNS * 1.0 / 4.0);

		if(turn <= turnMax) {
			return start + (int) ((middle - start)*(turn/turnMax));
		} else {
			return middle - (int) ((middle - end)*(turn-turnMax)/(Constants.MAX_TURNS-turnMax));
		}
	}
	
	public Position getHighestConcentration(Position from) {
		Position highest_concentration = null;
		double value = -1;
		GameMap game_map = game.gameMap;
		for(int i = 0; i < game_map.width; ++i){
			for(int j = 0; j < game_map.height; ++j){
				Position pos = new Position(i, j);
				int temp_distance = game_map.calculateDistance(from, game_map.normalize(new Position(pos.x + 2, pos.y + 2)));
				if(pos.equals(from) || search_dist < temp_distance) {
					continue;
				}
				int temp = 0;
				int rad = 4;
				for(int x = 0; x < rad; ++ x) {
					for(int y = 0; y < rad; ++y) {
						temp += game_map.at(game_map.normalize(new Position(x + i,y + j))).halite;
					}
				}
				temp -= botsWithFocusPosition(pos) * 100;
				temp /= (temp_distance+1)*.1;
				if(temp > value) {
					value = temp;
					highest_concentration = game_map.normalize(new Position(pos.x + 2, pos.y + 2));
				}
			}
		}
		return highest_concentration;
	}
	
	public void update() {
        game.updateFrame();
        makingDropoff = false;
        
        if(game.turnNumber != 0 && game.turnNumber % 10 == 0) {
        	search_dist += 3;
        }

        HashMap<EntityId, ShipController2> new_ship_controllers = new HashMap<>();
        for(Ship ship : game.me.ships.values()) {
        	if(ship_controllers.containsKey(ship.id)) {
        		new_ship_controllers.put(ship.id, ship_controllers.get(ship.id));
        		new_ship_controllers.get(ship.id).update(ship);
        	}else {
        		new_ship_controllers.put(ship.id, new ShipController2(ship));
        	}
        }
        
    	ship_controllers = new_ship_controllers;

    	total_halite = 0;
		for(int i = 0; i < game.gameMap.width; ++i){
			for(int j = 0; j < game.gameMap.height; ++j){
				total_halite += game.gameMap.at(new Position(i, j)).halite;
			}
		}

        total_ships = 0;
       	for(Player player : game.players) {
       		total_ships += player.ships.size();
       	}

       	dropoff_timer = Math.max(0, dropoff_timer-1);

       	buffer = 0;
        if(game.turnNumber >= ((game.gameMap.width <= 40 ? 145.0 : 160.0) * (32.0 / game.gameMap.width)) && dropoff_timer == 0 ) {
        	buffer = Constants.DROPOFF_COST;
        }
        submitCommands();
	}
	
	private void submitCommands() {
        final ArrayList<Command> command_queue = new ArrayList<>();
        ArrayList<ShipController2> ships_controls = new ArrayList<ShipController2>(ship_controllers.values());

        Collections.sort(ships_controls, new Comparator<ShipController2>(){

            @Override
            public int compare(final ShipController2 o1, final ShipController2 o2){
                // let your comparator look up your car's color in the custom order
                return Integer.compare(o1.state.getValue(), o2.state.getValue());
            }
        });
        
        ship_positions.clear();
        
        for (final ShipController2 ship_controller : ships_controls) {
        	command_queue.add(ship_controller.getCommand(this));
        }       	

        if (
        	(total_halite / (total_ships+1)) >= ((game.gameMap.width <= 48 ? 1.5 : 1.5 ) *Constants.SHIP_COST)/((double) (Math.max(Constants.MAX_TURNS - game.turnNumber, 100) / 100)) &&
            game.turnNumber <= Constants.MAX_TURNS * (game.gameMap.width <= 48 ? 5.6 : 5.5)/8 &&
            ship_controllers.size() <= (Constants.MAX_TURNS - 300) * .5 &&
            game.me.halite >= Constants.SHIP_COST  + buffer &&
            !ship_positions.contains(game.me.shipyard.position)
            )
        {
        	command_queue.add(game.me.shipyard.spawn());
        }

        game.endTurn(command_queue);
	}
}
