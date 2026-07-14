package emu.game.pathfinding

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import kotlin.math.abs

/** Movement visible to the player-info builder during the current cycle. */
sealed interface MovementUpdate {
    val deltaX: Int
    val deltaY: Int

    data object Idle : MovementUpdate {
        override val deltaX = 0
        override val deltaY = 0
    }

    data class Walk(override val deltaX: Int, override val deltaY: Int) : MovementUpdate

    data class Run(override val deltaX: Int, override val deltaY: Int) : MovementUpdate
}

/**
 * Size-one player route and movement state.
 *
 * A walking cycle validates and consumes one tile; a running cycle consumes at most two. Dynamic
 * collision is checked for every individual step, so a blocked waypoint remains queued and is
 * retried rather than allowing an entity to walk through a newly-placed obstacle.
 */
class PlayerMovement(
    initialPosition: Tile,
    private val collisionMap: CollisionMap,
    private val pathfinder: BfsPathfinder = BfsPathfinder(collisionMap),
    private val extraCollisionFlag: Int = CollisionFlag.BLOCK_PLAYERS,
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

    /** Replaces the current route with a newly searched route to [destination]. */
    fun routeTo(destination: Tile, temporaryRun: Boolean? = null): PathRoute {
        val route = pathfinder.findPath(position, destination)
        queueRoute(route, temporaryRun)
        return route
    }

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
    fun process() {
        update = MovementUpdate.Idle
        val start = position
        val firstStep = takeStep()
        if ((temporaryRun ?: runEnabled) && firstStep) takeStep()
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

    /** Cycle processes that advance movement in `PLAYER` and clear its delta in `CLEANUP`. */
    fun cycleProcesses(): List<CycleProcess> =
        listOf(
            CycleProcess(CyclePhase.PLAYER) { process() },
            CycleProcess(CyclePhase.CLEANUP) { finishCycle() },
        )

    private fun takeStep(): Boolean {
        while (waypoints.firstOrNull() == position) waypoints.removeFirst()
        val target = waypoints.firstOrNull() ?: return false
        val deltaX = (target.x - position.x).coerceIn(-1, 1)
        val deltaY = (target.y - position.y).coerceIn(-1, 1)
        val step =
            when {
                collisionMap.canTravel(position, deltaX, deltaY, extraCollisionFlag) -> deltaX to deltaY
                deltaX != 0 && collisionMap.canTravel(position, deltaX, 0, extraCollisionFlag) -> deltaX to 0
                deltaY != 0 && collisionMap.canTravel(position, 0, deltaY, extraCollisionFlag) -> 0 to deltaY
                else -> return false
            }
        position = Tile(position.x + step.first, position.y + step.second, position.plane)
        if (position == target) waypoints.removeFirst()
        return true
    }
}
