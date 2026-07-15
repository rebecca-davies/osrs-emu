package emu.game.pathfinding

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

    var position: Tile = initialPosition
        private set

    var runEnabled: Boolean = false

    var update: MovementUpdate = MovementUpdate.Idle
        private set

    val hasRoute: Boolean
        get() = waypoints.isNotEmpty()

    /** Replaces the current compressed waypoint queue with [route]. */
    fun queueRoute(route: PathRoute, temporaryRun: Boolean? = null) {
        waypoints.clear()
        this.temporaryRun = null
        if (route.failed) return
        require(route.waypoints.all { it.plane == position.plane }) { "route changes plane without teleporting" }
        waypoints.addAll(route.waypoints)
        this.temporaryRun = temporaryRun
    }

    /** Advances the route for one player phase and records its net player-info delta. */
    fun process(
        collisionMap: CollisionMap,
        extraCollisionFlag: Int = CollisionFlag.BLOCK_PLAYERS,
    ) {
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
        update = MovementUpdate.Idle
    }

    private fun takeStep(collisionMap: CollisionMap, extraCollisionFlag: Int): Boolean {
        while (waypoints.firstOrNull() == position) waypoints.removeFirst()
        val target = waypoints.firstOrNull() ?: return false
        val deltaX = (target.x - position.x).coerceIn(-1, 1)
        val deltaY = (target.y - position.y).coerceIn(-1, 1)
        val step =
            when {
                collisionMap.canTravel(
                    position.x,
                    position.y,
                    position.plane,
                    deltaX,
                    deltaY,
                    extraCollisionFlag,
                ) -> deltaX to deltaY
                deltaX != 0 &&
                    collisionMap.canTravel(
                        position.x,
                        position.y,
                        position.plane,
                        deltaX,
                        0,
                        extraCollisionFlag,
                    ) -> deltaX to 0
                deltaY != 0 &&
                    collisionMap.canTravel(
                        position.x,
                        position.y,
                        position.plane,
                        0,
                        deltaY,
                        extraCollisionFlag,
                    ) -> 0 to deltaY
                else -> return false
            }
        position = Tile(position.x + step.first, position.y + step.second, position.plane)
        if (position == target) waypoints.removeFirst()
        return true
    }
}
