package emu.game.queue

import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerTimerTest {
    @Test
    fun `normal timer waits for access while soft timer does not`() {
        val calls = mutableListOf<String>()
        val timers = PlayerTimers()
        timers.set("normal", PlayerTimerType.NORMAL, intervalTicks = 2, currentTick = 10) {
            calls += "normal"
        }
        timers.set("soft", PlayerTimerType.SOFT, intervalTicks = 2, currentTick = 10) {
            calls += "soft"
        }

        runSuspending { timers.process(PlayerTimerType.NORMAL, currentTick = 12, canAccess = { false }) }
        runSuspending { timers.process(PlayerTimerType.SOFT, currentTick = 12, canAccess = { false }) }
        assertEquals(listOf("soft"), calls)

        runSuspending { timers.process(PlayerTimerType.NORMAL, currentTick = 13, canAccess = { true }) }
        assertEquals(listOf("soft", "normal"), calls)
    }

    @Test
    fun `timer resets to current tick without catch-up bursts`() {
        var calls = 0
        val timers = PlayerTimers()
        timers.set("timer", PlayerTimerType.NORMAL, intervalTicks = 2, currentTick = 0) { calls++ }

        runSuspending { timers.process(PlayerTimerType.NORMAL, currentTick = 9, canAccess = { true }) }
        runSuspending { timers.process(PlayerTimerType.NORMAL, currentTick = 10, canAccess = { true }) }
        runSuspending { timers.process(PlayerTimerType.NORMAL, currentTick = 11, canAccess = { true }) }

        assertEquals(2, calls)
    }
}
