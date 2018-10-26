
import hlt
from hlt import constants
from hlt.positionals import Direction
import random

import logging

game = hlt.Game()
game.ready("20kBot")
logging.info("Successfully created bot! My Player ID is {}.".format(game.my_id))

ship_states = {}

# one of x,y = 0
def getDir(x,y):
    if x > 0:
        return Direction.North
    if x < 0:
        return Direction.South
    if y > 0:
        return Direction.East
    if y < 0: 
        return Direction.West

def navigate(moves, position_dict, shipyard):
    weights = {}
    for direction in moves:
        position = position_dict[direction]
        weights[direction] = abs(shipyard.x - position.x) + abs(shipyard.y - position.y)
    if len(weights) == 0:
        return Direction.Still
    return min(weights, key=weights.get)

while True:
    game.update_frame()
    me = game.me
    game_map = game.game_map

    command_queue = []
    directionOrder = [ Direction.North, Direction.South, Direction.East, Direction.West, Direction.Still ]
    positionChoices = [] # contains choices for ships
    for ship in me.get_ships():
        if ship.id not in ship_states:
            ship_states[ship.id] = "collecting"
        positionOptions = ship.position.get_surrounding_cardinals() + [ship.position]
        position_dict = {}
        halite_dict = {}

        #if close to full need to go back

        for n,direction in enumerate(directionOrder):
            position_dict[direction] = positionOptions[n]

        for direction in position_dict:
            position = position_dict[direction]
            halite = game_map[position].halite_amount
            if position_dict[direction] not in positionChoices:
                if direction == Direction.Still:
                    halite_dict[direction] = halite*4
                else:
                    halite_dict[direction] = halite
            else:
                logging.info("attempting at filled position\n")

        if ship_states[ship.id] == "depositing":
            move = navigate(halite_dict, position_dict, me.shipyard.position)
            positionChoices.append(position_dict[move])
            command_queue.append(ship.move(move))
            if direction == Direction.Still and ship.position == me.shipyard.position:
                ship_states[ship.id] = "collecting"
        elif ship_states[ship.id] == "collecting":
            directionalChoice = Direction.Still
            if not len(halite_dict) == 0:
                directionalChoice = max(halite_dict, key=halite_dict.get)
            positionChoices.append(position_dict[directionalChoice])
            command_queue.append(ship.move(game_map.naive_navigate(ship, position_dict[directionalChoice])))
            if ship.halite_amount > constants.MAX_HALITE * 0.9:
                ship_states[ship.id] = "depositing"

            if game.turn_number > constants.MAX_TURNS - game_map.calculate_distance(ship.position, me.shipyard.position) - 20:
                ship_states[ship.id] = "depositing"

    if game.turn_number <= 200 and me.halite_amount >= constants.SHIP_COST and not game_map[me.shipyard].is_occupied:
        command_queue.append(me.shipyard.spawn())

    game.end_turn(command_queue)
