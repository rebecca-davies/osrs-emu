package emu.game.pathfinding

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Result of submitting a destination to a bounded route mailbox. */
enum class RouteRequestAdmission {
    QUEUED,
    REPLACED,
    REJECTED,
}

/** Monotonic route-input counters. */
data class RouteRequestMetrics(
    val submitted: Long,
    val replaced: Long,
    val rejected: Long,
    val processed: Long,
)

/** Thread-safe destination sink exposed to network packet handlers. */
fun interface PlayerRouteRequestSink {
    fun submit(x: Int, z: Int, keyCombination: Int): RouteRequestAdmission
}

/**
 * Size-one, latest-wins network-to-game mailbox for player route destinations.
 *
 * IO may only [submit]. A newer click atomically replaces an unprocessed click, and the world
 * consumes at most one destination in `CLIENT_INPUT` before movement. This bounds both memory and
 * BFS work while matching the client's most recent intent.
 */
class PlayerRouteRequestQueue : PlayerRouteRequestSink {
    private val pending = AtomicReference<Request?>()
    private val submitted = AtomicLong()
    private val replaced = AtomicLong()
    private val rejected = AtomicLong()
    private val processed = AtomicLong()

    /** Offers raw wire coordinates without reading mutable world state from the network thread. */
    override fun submit(x: Int, z: Int, keyCombination: Int): RouteRequestAdmission {
        submitted.incrementAndGet()
        if (x !in WORLD_COORDINATES || z !in WORLD_COORDINATES) {
            rejected.incrementAndGet()
            return RouteRequestAdmission.REJECTED
        }
        val previous = pending.getAndSet(Request(x, z, keyCombination))
        return if (previous == null) {
            RouteRequestAdmission.QUEUED
        } else {
            replaced.incrementAndGet()
            RouteRequestAdmission.REPLACED
        }
    }

    fun metrics(): RouteRequestMetrics =
        RouteRequestMetrics(
            submitted = submitted.get(),
            replaced = replaced.get(),
            rejected = rejected.get(),
            processed = processed.get(),
        )

    /** Creates the client-input process that searches at most the latest admitted destination. */
    fun cycleProcesses(movement: PlayerMovement): List<CycleProcess> =
        listOf(
            CycleProcess(CyclePhase.CLIENT_INPUT) {
                val request = pending.getAndSet(null) ?: return@CycleProcess
                processed.incrementAndGet()
                val destination = Tile(request.x, request.z, movement.position.plane)
                val temporaryRun = if (request.keyCombination == CONTROL_KEY) !movement.runEnabled else null
                movement.routeTo(destination, temporaryRun)
            },
        )

    private data class Request(val x: Int, val z: Int, val keyCombination: Int)

    private companion object {
        const val CONTROL_KEY = 1
        val WORLD_COORDINATES = 0..0x3FFF
    }
}
