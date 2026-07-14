package emu.game.pathfinding

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import java.util.concurrent.ArrayBlockingQueue

/** Thread-safe destination sink exposed to network packet handlers. */
fun interface PlayerRouteRequestSink {
    fun submit(x: Int, z: Int, keyCombination: Int): Boolean
}

/**
 * Bounded network-to-game mailbox for player route destinations.
 *
 * IO may only [submit]; the world thread drains at most [maxPerCycle] requests during
 * `CLIENT_INPUT`, before the player's movement phase. Later clicks replace earlier routes in FIFO
 * order exactly as multiple move packets decoded in one game cycle would.
 */
class PlayerRouteRequestQueue(
    capacity: Int = DEFAULT_CAPACITY,
    private val maxPerCycle: Int = DEFAULT_MAX_PER_CYCLE,
) : PlayerRouteRequestSink {
    private val requests: ArrayBlockingQueue<Request>

    init {
        require(capacity > 0) { "route request capacity must be positive" }
        require(maxPerCycle > 0) { "per-cycle route request limit must be positive" }
        requests = ArrayBlockingQueue(capacity)
    }

    /** Offers raw wire coordinates without reading mutable world state from the network thread. */
    override fun submit(x: Int, z: Int, keyCombination: Int): Boolean =
        requests.offer(Request(x, z, keyCombination))

    /** Creates the client-input process that searches each admitted destination. */
    fun cycleProcesses(movement: PlayerMovement): List<CycleProcess> =
        listOf(
            CycleProcess(CyclePhase.CLIENT_INPUT) {
                repeat(maxPerCycle) {
                    val request = requests.poll() ?: return@CycleProcess
                    if (request.x !in WORLD_COORDINATES || request.z !in WORLD_COORDINATES) return@repeat
                    val destination = Tile(request.x, request.z, movement.position.plane)
                    val temporaryRun = if (request.keyCombination == CONTROL_KEY) !movement.runEnabled else null
                    movement.routeTo(destination, temporaryRun)
                }
            },
        )

    private data class Request(val x: Int, val z: Int, val keyCombination: Int)

    private companion object {
        const val DEFAULT_CAPACITY = 32
        const val DEFAULT_MAX_PER_CYCLE = 10
        const val CONTROL_KEY = 1
        val WORLD_COORDINATES = 0..0x3FFF
    }
}
