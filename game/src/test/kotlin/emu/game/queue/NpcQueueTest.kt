package emu.game.queue

import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals

class NpcQueueTest {
    @Test
    fun `npc delay countdown pauses while npc is delayed or inactive`() {
        val calls = mutableListOf<String>()
        val queue = NpcQueue()
        queue.enqueue(delayTicks = 2) { calls += "ai_queue" }

        runSuspending { queue.process(isActive = { true }, isDelayed = { true }) }
        runSuspending { queue.process(isActive = { false }, isDelayed = { false }) }
        runSuspending { queue.process(isActive = { true }, isDelayed = { false }) }
        assertEquals(emptyList(), calls)
        runSuspending { queue.process(isActive = { true }, isDelayed = { false }) }

        assertEquals(listOf("ai_queue"), calls)
    }

    @Test
    fun `zero delay npc action runs on its first eligible queue pass`() {
        var calls = 0
        val queue = NpcQueue()
        queue.enqueue(delayTicks = 0) { calls++ }

        runSuspending { queue.process(isActive = { true }, isDelayed = { false }) }

        assertEquals(1, calls)
        assertEquals(0, queue.size)
    }

    @Test
    fun `npc state is rechecked between actions in one pass`() {
        val calls = mutableListOf<String>()
        var delayed = false
        val queue = NpcQueue()
        queue.enqueue(0) {
            calls += "first"
            delayed = true
        }
        queue.enqueue(0) { calls += "second" }

        runSuspending { queue.process(isActive = { true }, isDelayed = { delayed }) }

        assertEquals(listOf("first"), calls)
        assertEquals(1, queue.size)
    }
}
