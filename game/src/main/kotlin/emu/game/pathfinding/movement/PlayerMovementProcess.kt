package emu.game.pathfinding.movement

import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.route.BfsPathfinder
import emu.game.pathfinding.route.PathRoute
import emu.game.pathfinding.route.PlayerRouteFinder

/** World-scoped route search and collision validation for player movement state. */
class PlayerMovementProcess(
    private val collisionMap: CollisionMap,
    private val pathfinder: BfsPathfinder = BfsPathfinder(collisionMap),
) : PlayerRouteFinder {
    /** Replaces [movement]'s route with a search from its authoritative current tile. */
    override fun routeTo(
        movement: PlayerMovement,
        destination: Tile,
        temporaryRun: Boolean?,
    ): PathRoute {
        val route = pathfinder.findPath(movement.position, destination)
        movement.queueRoute(route, temporaryRun)
        return route
    }

    /** Advances one player's route during the global player phase. */
    fun process(movement: PlayerMovement) {
        movement.process(collisionMap)
    }
}
