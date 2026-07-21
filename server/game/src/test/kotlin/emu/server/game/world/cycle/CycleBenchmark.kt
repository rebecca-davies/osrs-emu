package emu.server.game.world.cycle

import com.sun.management.ThreadMXBean
import emu.compression.HuffmanCodec
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.server.game.TestPlayerContent
import emu.server.game.network.output.GameOutputSink
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.player.process.PlayerChatActionProcess
import emu.server.game.world.player.process.PlayerOutputProcess
import emu.server.game.world.testWorld
import java.lang.management.ManagementFactory
import java.util.Locale

/** Runs a repeatable, headless benchmark through every world-cycle phase. */
fun main(args: Array<String>) {
    val playerCount = args.intAt(0, 250, "players")
    val warmupCycles = args.intAt(1, 100, "warmup cycles")
    val measuredCycles = args.intAt(2, 200, "measured cycles")
    require(playerCount in 1..PlayerCapacity.PER_WORLD) {
        "players must be in 1..${PlayerCapacity.PER_WORLD}"
    }
    require(warmupCycles >= 0) { "warmup cycles must be non-negative" }
    require(measuredCycles > 0) { "measured cycles must be positive" }

    var publishedBatches = 0L
    val world = testWorld(maxPlayerIndex = playerCount)
    repeat(playerCount) { ordinal ->
        val id = ordinal + 1L
        val x = SPAWN_X + ordinal % LOCAL_WIDTH
        val y = SPAWN_Y + ordinal / LOCAL_WIDTH % LOCAL_WIDTH
        val connected =
            world.addTestPlayer(
                CharacterRecord(id, "Bench$id", CharacterPosition(x, y, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink {
                    publishedBatches++
                    true
                },
            )
        world.activateTestPlayer(connected.connection.token)
    }

    val movement = PlayerMovementProcess(OpenCollisionMap)
    val cycle =
        WorldCycle(
            world,
            WorldCommandQueue(capacity = 8),
            TestPlayerContent.actions(
                movement,
                PlayerChatActionProcess(
                    HuffmanCodec(ByteArray(256) { 8 }),
                    ChatAuditSink { true },
                ),
            ),
            TestPlayerContent.main(movement),
            TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
            PlayerOutputProcess(),
        )

    var worldTick = 0L
    repeat(warmupCycles) {
        val batchesBefore = publishedBatches
        cycle.tick(worldTick++)
        checkPublishedPlayerCount(publishedBatches - batchesBefore, playerCount, worldTick - 1)
    }
    val thread = Thread.currentThread().threadId()
    val allocation = allocationCounter()
    val timings = LongArray(measuredCycles)
    val allocatedBefore = allocation?.getThreadAllocatedBytes(thread) ?: -1L
    repeat(measuredCycles) { index ->
        val batchesBefore = publishedBatches
        val started = System.nanoTime()
        cycle.tick(worldTick++)
        timings[index] = System.nanoTime() - started
        checkPublishedPlayerCount(publishedBatches - batchesBefore, playerCount, worldTick - 1)
    }
    val allocatedAfter = allocation?.getThreadAllocatedBytes(thread) ?: -1L
    check(world.activePlayers().size == playerCount) {
        "benchmark world lost active players: expected=$playerCount actual=${world.activePlayers().size}"
    }

    timings.sort()
    val total = timings.sum()
    val average = total.toDouble() / measuredCycles
    val allocatedPerCycle =
        if (allocatedBefore >= 0 && allocatedAfter >= allocatedBefore) {
            (allocatedAfter - allocatedBefore).toDouble() / measuredCycles
        } else {
            Double.NaN
        }
    println(
        "cycle-benchmark players=$playerCount warmup=$warmupCycles cycles=$measuredCycles " +
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

private const val SPAWN_X = 3_200
private const val SPAWN_Y = 3_200
private const val LOCAL_WIDTH = 16
