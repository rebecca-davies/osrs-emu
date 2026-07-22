package emu.server.game.runtime.lifecycle

import emu.game.cycle.FixedRateTickSchedule
import emu.game.cycle.GAME_TICK_MILLIS
import emu.server.game.world.cycle.WorldCycle
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private val logger = KotlinLogging.logger {}

/** Schedules one explicit [WorldCycle] on the authoritative world thread. */
class WorldRuntime(
    private val cycle: WorldCycle,
    tickInterval: Duration = GAME_TICK_MILLIS.milliseconds,
) {
    private val intervalMillis = tickInterval.inWholeMilliseconds
    private val started = AtomicBoolean(false)

    init {
        require(intervalMillis > 0) { "tick interval must be at least one millisecond" }
    }

    /** Runs immediately, then maintains the fixed 600 ms production cadence. */
    suspend fun run(maxTicks: Long = Long.MAX_VALUE) {
        require(maxTicks >= 0) { "maximum tick count must be non-negative" }
        check(started.compareAndSet(false, true)) { "world runtime can only be started once" }
        val schedule = FixedRateTickSchedule(intervalMillis)
        var worldTick = 0L
        try {
            while (worldTick < maxTicks) {
                val startedAtMillis = monotonicMillis()
                cycle.tick(worldTick)
                worldTick++
                if (worldTick < maxTicks) {
                    val wait = schedule.delayAfterTick(startedAtMillis, monotonicMillis())
                    if (wait > 0) delay(wait) else yield()
                }
            }
        } finally {
            withContext(NonCancellable) {
                cycle.beginShutdown()
                var stopped = false
                for (shutdownCycle in 0 until MAX_SHUTDOWN_CYCLES) {
                    if (cycle.shutdownStep()) {
                        stopped = true
                        break
                    }
                    if (shutdownCycle < NORMAL_SHUTDOWN_CYCLES) delay(intervalMillis) else yield()
                }
                if (!stopped) {
                    logger.error { "world: forcing terminal shutdown after $MAX_SHUTDOWN_CYCLES cycles" }
                    cycle.forceShutdown()
                }
            }
            logger.info { "world: stopped after $worldTick cycles" }
        }
    }

    private companion object {
        const val NORMAL_SHUTDOWN_CYCLES = 2
        const val MAX_SHUTDOWN_CYCLES = 1_024
        const val NANOS_PER_MILLISECOND = 1_000_000L

        fun monotonicMillis(): Long = System.nanoTime() / NANOS_PER_MILLISECOND
    }
}
