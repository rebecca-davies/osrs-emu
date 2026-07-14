package emu.game.queue

import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerQueueCycleTest {
    @Test
    fun `player work drains primary weak normal timer soft timer then engine`() {
        val calls = mutableListOf<String>()
        val cycle = PlayerQueueCycle()
        cycle.queues.enqueue(PlayerQueueType.NORMAL, 0) { calls += "primary" }
        cycle.queues.enqueue(PlayerQueueType.WEAK, 0) { calls += "weak" }
        cycle.timers.set("normal", PlayerTimerType.NORMAL, 1, currentTick = 0) { calls += "normal timer" }
        cycle.timers.set("soft", PlayerTimerType.SOFT, 1, currentTick = 0) { calls += "soft timer" }
        cycle.queues.enqueueEngine { calls += "engine" }

        runSuspending { cycle.process(currentTick = 1, canAccess = { true }) }

        assertEquals(
            listOf("primary", "weak", "normal timer", "soft timer", "engine"),
            calls,
        )
    }
}
