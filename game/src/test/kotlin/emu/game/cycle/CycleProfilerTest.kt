package emu.game.cycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleProfilerTest {
    @Test
    fun `reports average max and overruns once per window then resets`() {
        val profiler =
            CycleProfiler(
                reportIntervalNanos = 30_000,
                tickBudgetNanos = 600,
                startedAtNanos = 0,
            )

        val first = profiler.record(durationNanos = 100, finishedAtNanos = 10_000)
        val second = profiler.record(durationNanos = 700, finishedAtNanos = 20_000)
        val third = profiler.record(durationNanos = 400, finishedAtNanos = 30_000)

        assertFalse(first.lagSpike)
        assertNull(first.snapshot)
        assertTrue(second.lagSpike)
        assertNull(second.snapshot)
        val snapshot = requireNotNull(third.snapshot)
        assertEquals(3, snapshot.cycles)
        assertEquals(400, snapshot.averageNanos)
        assertEquals(700, snapshot.maxNanos)
        assertEquals(1, snapshot.lagSpikes)
        assertEquals(30_000, snapshot.windowNanos)

        val reset = profiler.record(durationNanos = 50, finishedAtNanos = 40_000)
        assertNull(reset.snapshot)
    }

    @Test fun `rejects negative durations and timestamps that move backwards`() {
        val profiler = CycleProfiler(startedAtNanos = 10)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            profiler.record(durationNanos = -1, finishedAtNanos = 11)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            profiler.record(durationNanos = 1, finishedAtNanos = 9)
        }
    }
}
