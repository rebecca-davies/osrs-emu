package emu.server.game.world.cycle

import com.sun.management.ThreadMXBean
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.player.Player
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.server.game.TestPlayerContent
import emu.server.game.network.output.GameOutputSink
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.World
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.testWorld
import java.lang.management.ManagementFactory
import java.util.Locale

/** Runs a repeatable, headless benchmark through every world-cycle phase. */
fun main(args: Array<String>) {
    val playerCount = args.intAt(0, 250, "players")
    val warmupCycles = args.intAt(1, 100, "warmup cycles")
    val measuredCycles = args.intAt(2, 200, "measured cycles")
    val workload = BenchmarkWorkload.parse(args.getOrNull(3) ?: "idle")
    require(playerCount in 1..PlayerCapacity.PER_WORLD) {
        "players must be in 1..${PlayerCapacity.PER_WORLD}"
    }
    require(warmupCycles >= 0) { "warmup cycles must be non-negative" }
    require(measuredCycles > 0) { "measured cycles must be positive" }

    var publishedBatches = 0L
    val world = testWorld(maxPlayerIndex = playerCount)
    val players = ArrayList<Player>(playerCount)
    repeat(playerCount) { ordinal ->
        val id = ordinal + 1L
        val player =
            world.addTestPlayer(
                CharacterRecord(id, "Bench$id", workload.initialPosition(ordinal), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink {
                    publishedBatches++
                    true
                },
            )
        world.activateTestPlayer(world.session(player).token)
        players += player
    }

    val cycle = TestPlayerContent.cycle(world, WorldCommandQueue(capacity = 8))

    var worldTick = 0L
    repeat(warmupCycles) {
        workload.prepare(worldTick, world, players)
        val batchesBefore = publishedBatches
        cycle.tick(worldTick++)
        checkPublishedPlayerCount(publishedBatches - batchesBefore, playerCount, worldTick - 1)
    }
    val thread = Thread.currentThread().threadId()
    val allocation = allocationCounter()
    val timings = LongArray(measuredCycles)
    var allocatedBytes = 0L
    var allocationAvailable = allocation != null
    repeat(measuredCycles) { index ->
        workload.prepare(worldTick, world, players)
        val batchesBefore = publishedBatches
        val allocatedBefore = allocation?.getThreadAllocatedBytes(thread) ?: -1L
        val started = System.nanoTime()
        cycle.tick(worldTick++)
        timings[index] = System.nanoTime() - started
        val allocatedAfter = allocation?.getThreadAllocatedBytes(thread) ?: -1L
        if (allocatedBefore >= 0 && allocatedAfter >= allocatedBefore) {
            allocatedBytes += allocatedAfter - allocatedBefore
        } else {
            allocationAvailable = false
        }
        checkPublishedPlayerCount(publishedBatches - batchesBefore, playerCount, worldTick - 1)
    }
    check(world.activePlayers().size == playerCount) {
        "benchmark world lost active players: expected=$playerCount actual=${world.activePlayers().size}"
    }

    timings.sort()
    val total = timings.sum()
    val average = total.toDouble() / measuredCycles
    val allocatedPerCycle =
        if (allocationAvailable) allocatedBytes.toDouble() / measuredCycles else Double.NaN
    println(
        "cycle-benchmark workload=${workload.argument} players=$playerCount " +
            "warmup=$warmupCycles cycles=$measuredCycles " +
            "avg=${average.milliseconds()}ms p50=${timings.percentile(0.50).toDouble().milliseconds()}ms " +
            "p95=${timings.percentile(0.95).toDouble().milliseconds()}ms " +
            "max=${timings.last().toDouble().milliseconds()}ms " +
            "allocated=${allocatedPerCycle.bytes()} bytes/cycle batches=$publishedBatches",
    )
}

private fun checkPublishedPlayerCount(actual: Long, expected: Int, worldTick: Long) {
    check(actual == expected.toLong()) {
        "world tick $worldTick published $actual batches for $expected active players"
    }
}

private fun Array<String>.intAt(index: Int, default: Int, label: String): Int {
    val value = getOrNull(index) ?: return default
    return requireNotNull(value.toIntOrNull()) { "$label must be an integer" }
}

private fun allocationCounter(): ThreadMXBean? =
    (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.also {
        if (it.isThreadAllocatedMemorySupported && !it.isThreadAllocatedMemoryEnabled) {
            it.isThreadAllocatedMemoryEnabled = true
        }
    }?.takeIf(ThreadMXBean::isThreadAllocatedMemoryEnabled)

private fun LongArray.percentile(fraction: Double): Long =
    this[((size - 1) * fraction).toInt()]

private fun Double.milliseconds(): String = String.format(Locale.ROOT, "%.3f", this / 1_000_000.0)

private fun Double.bytes(): String = String.format(Locale.ROOT, "%.0f", this)

private enum class BenchmarkWorkload(val argument: String) {
    IDLE("idle") {
        override fun initialPosition(ordinal: Int): CharacterPosition =
            CharacterPosition(
                BENCHMARK_CENTRE_X + ordinal % LOCAL_WIDTH,
                BENCHMARK_CENTRE_Y + ordinal / LOCAL_WIDTH % LOCAL_WIDTH,
                0,
            )

        override fun prepare(worldTick: Long, world: World, players: List<Player>) = Unit
    },
    MOVING_BOTS("moving-bots") {
        override fun initialPosition(ordinal: Int): CharacterPosition =
            CharacterPosition(BOT_MOVEMENT_CENTRE_X, BOT_MOVEMENT_CENTRE_Y, 0)

        override fun prepare(worldTick: Long, world: World, players: List<Player>) {
            for (index in players.indices) {
                if ((worldTick + index) % BOT_MOVEMENT_CYCLES != 0L) continue
                val movementNumber = (worldTick + index) / BOT_MOVEMENT_CYCLES
                val destination = botDestination(index, movementNumber)
                check(
                    destination.x in BOT_MOVEMENT_CENTRE_X - BOT_MOVEMENT_RADIUS..
                        BOT_MOVEMENT_CENTRE_X + BOT_MOVEMENT_RADIUS &&
                        destination.y in BOT_MOVEMENT_CENTRE_Y - BOT_MOVEMENT_RADIUS..
                        BOT_MOVEMENT_CENTRE_Y + BOT_MOVEMENT_RADIUS,
                ) {
                    "moving-bot destination escaped the shared movement area: $destination"
                }
                check(world.session(players[index]).actions.submit(PlayerAction.Route(destination.x, destination.y))) {
                    "benchmark action queue rejected player ${index + 1}"
                }
            }
        }
    };

    abstract fun initialPosition(ordinal: Int): CharacterPosition

    abstract fun prepare(worldTick: Long, world: World, players: List<Player>)

    companion object {
        fun parse(argument: String): BenchmarkWorkload =
            entries.firstOrNull { it.argument == argument.lowercase() }
                ?: error("workload must be one of ${entries.joinToString { it.argument }}")
    }
}

private fun botDestination(index: Int, movementNumber: Long): CharacterPosition =
    CharacterPosition(
        BOT_MOVEMENT_CENTRE_X +
            Math.floorMod(index.toLong() + movementNumber, BOT_MOVEMENT_DIAMETER.toLong()).toInt() -
            BOT_MOVEMENT_RADIUS,
        BOT_MOVEMENT_CENTRE_Y +
            Math.floorMod(index.toLong() * 7 + movementNumber * 5, BOT_MOVEMENT_DIAMETER.toLong()).toInt() -
            BOT_MOVEMENT_RADIUS,
        0,
    )

private const val BENCHMARK_CENTRE_X = 3_200
private const val BENCHMARK_CENTRE_Y = 3_200
private const val LOCAL_WIDTH = 16
private const val BOT_MOVEMENT_CENTRE_X = 3_222
private const val BOT_MOVEMENT_CENTRE_Y = 3_218
private const val BOT_MOVEMENT_CYCLES = 5
private const val BOT_MOVEMENT_RADIUS = 6
private const val BOT_MOVEMENT_DIAMETER = BOT_MOVEMENT_RADIUS * 2 + 1
