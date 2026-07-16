package emu.game.pathfinding.route

import emu.game.map.Tile
import emu.game.pathfinding.movement.PlayerMovement

/** Narrow route-search capability used while applying a player's queued route action. */
interface PlayerRouteFinder {
    fun routeTo(
        movement: PlayerMovement,
        destination: Tile,
        temporaryRun: Boolean? = null,
    ): PathRoute
}
