package emu.game.pathfinding.movement

import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.canTravel
import emu.game.pathfinding.route.PathRoute
import kotlin.math.abs

/**
 * Size-one player route and movement state.
 *
 * A walking cycle validates and consumes one tile; a running cycle consumes at most two. Dynamic
 * collision is checked for every individual step, so a blocked waypoint remains queued and is
 * retried rather than allowing an entity to walk through a newly-placed obstacle.
 */
class PlayerMovement(
    initialPosition: Tile,
) {
    private val waypoints = ArrayDeque<Tile>()
    private var temporaryRun: Boolean? = null
    private var pathingTargetPosition: Tile? = null
    private var pathingTargetSize = 0
    private var teleportOrigin: Tile? = null

    var position: Tile = initialPosition
        private set

    var runEnabled: Boolean = false

    var update: MovementUpdate = MovementUpdate.Idle
        private set

    val hasRoute: Boolean
        get() = waypoints.isNotEmpty()

    /** Replaces the current compressed waypoint queue with [route]. */
    fun queueRoute(route: PathRoute, temporaryRun: Boolean? = null) {
        replaceRoute(route, temporaryRun, pathingTargetPosition = null, pathingTargetSize = 0)
    }

    /** Replaces the route while retaining the pathing-entity footprint it was calculated against. */
    internal fun queuePathingEntityRoute(
        route: PathRoute,
        position: Tile,
        size: Int,
        temporaryRun: Boolean?,
    ) {
        require(size in 1..0xFF) { "pathing target size must fit an unsigned byte" }
        replaceRoute(route, temporaryRun, position, size)
    }

    private fun replaceRoute(
        route: PathRoute,
        temporaryRun: Boolean?,
        pathingTargetPosition: Tile?,
        pathingTargetSize: Int,
    ) {
        waypoints.clear()
        this.temporaryRun = null
        this.pathingTargetPosition = pathingTargetPosition
        this.pathingTargetSize = pathingTargetSize
        if (route.failed) return
        require(route.waypoints.all { it.plane == position.plane }) { "route changes plane without teleporting" }
        waypoints.addAll(route.waypoints)
        this.temporaryRun = temporaryRun
    }

    /** Discards every remaining waypoint without changing the current tile. */
    fun clearRoute() {
        waypoints.clear()
        temporaryRun = null
        pathingTargetPosition = null
        pathingTargetSize = 0
    }

    /** Whether the current route was calculated for this exact pathing-entity footprint. */
    fun isRoutedTo(position: Tile, size: Int): Boolean =
        pathingTargetPosition == position && pathingTargetSize == size

    /** Moves immediately, discards the route, and retains one teleport update until cleanup. */
    fun teleportTo(destination: Tile) {
        val origin = teleportOrigin ?: position
        teleportOrigin = origin
        clearRoute()
        position = destination
        update =
            MovementUpdate.Teleport(
                deltaX = destination.x - origin.x,
                deltaY = destination.y - origin.y,
                planeDelta = (destination.plane - origin.plane) and 3,
            )
    }

    /** Advances the route for one player phase and records its net player-info delta. */
    internal fun advance(
        collisionMap: CollisionMap,
        extraCollisionFlag: Int = CollisionFlag.BLOCK_PLAYERS,
    ) {
        if (teleportOrigin != null) return
        update = MovementUpdate.Idle
        val start = position
        val firstStep = takeStep(collisionMap, extraCollisionFlag)
        if ((temporaryRun ?: runEnabled) && firstStep) takeStep(collisionMap, extraCollisionFlag)
        val deltaX = position.x - start.x
        val deltaY = position.y - start.y
        update =
            when {
                deltaX == 0 && deltaY == 0 -> MovementUpdate.Idle
                abs(deltaX) <= 1 && abs(deltaY) <= 1 -> MovementUpdate.Walk(deltaX, deltaY)
                else -> MovementUpdate.Run(deltaX, deltaY)
            }
        if (!hasRoute) temporaryRun = null
    }

    /** Clears only the ephemeral movement update; the remaining route persists across cycles. */
    fun finishCycle() {
        teleportOrigin = null
        update = MovementUpdate.Idle
    }

    private fun takeStep(collisionMap: CollisionMap, extraCollisionFlag: Int): Boolean {
        while (waypoints.firstOrNull() == position) waypoints.removeFirst()
        val target = waypoints.firstOrNull() ?: return false
        val deltaX = (target.x - position.x).coerceIn(-1, 1)
        val deltaY = (target.y - position.y).coerceIn(-1, 1)
        var stepX = deltaX
        var stepY = deltaY
        if (
            !collisionMap.canTravel(
                position.x,
                position.y,
                position.plane,
                stepX,
                stepY,
                extraCollisionFlag,
            )
        ) {
            when {
                deltaX != 0 &&
                    collisionMap.canTravel(
                        position.x,
                        position.y,
                        position.plane,
                        deltaX,
                        0,
                        extraCollisionFlag,
                    ) -> stepY = 0
                deltaY != 0 &&
                    collisionMap.canTravel(
                        position.x,
                        position.y,
                        position.plane,
                        0,
                        deltaY,
                        extraCollisionFlag,
                    ) -> stepX = 0
                else -> return false
            }
        }
        position = Tile(position.x + stepX, position.y + stepY, position.plane)
        if (position == target) waypoints.removeFirst()
        return true
    }
}
