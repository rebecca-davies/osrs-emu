package emu.game.cycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FixedRateTickScheduleTest {
    @Test
    fun `default cadence is one 600 millisecond game tick`() {
        val schedule = FixedRateTickSchedule()

        assertEquals(500L, schedule.delayAfterTick(startedAtMillis = 1_000, finishedAtMillis = 1_100))
        assertEquals(450L, schedule.delayAfterTick(startedAtMillis = 1_600, finishedAtMillis = 1_750))
    }

    @Test
    fun `overrun returns zero then catches up to the fixed timeline`() {
        val schedule = FixedRateTickSchedule()

        assertEquals(0L, schedule.delayAfterTick(startedAtMillis = 1_000, finishedAtMillis = 1_700))
        assertEquals(450L, schedule.delayAfterTick(startedAtMillis = 1_700, finishedAtMillis = 1_750))
    }

    @Test
    fun `invalid time and interval are rejected`() {
        assertFailsWith<IllegalArgumentException> { FixedRateTickSchedule(0) }
        val schedule = FixedRateTickSchedule()
        assertFailsWith<IllegalArgumentException> {
            schedule.delayAfterTick(startedAtMillis = 2, finishedAtMillis = 1)
        }
    }
}
