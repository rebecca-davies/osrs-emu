package emu.game.queue

import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldQueueTest {
    @Test
    fun `zero delay resumes after one complete intervening world cycle`() {
        val calls = mutableListOf<String>()
        val queue = WorldQueue()
        queue.enqueue(delayTicks = 0) { calls += "world" }

        runSuspending { queue.process() }
        assertEquals(emptyList(), calls)
        runSuspending { queue.process() }

        assertEquals(listOf("world"), calls)
        assertEquals(0, queue.size)
    }

    @Test
    fun `ready world actions retain FIFO order`() {
        val calls = mutableListOf<Int>()
        val queue = WorldQueue()
        queue.enqueue(0) { calls += 1 }
        queue.enqueue(0) { calls += 2 }

        repeat(2) { runSuspending { queue.process() } }

        assertEquals(listOf(1, 2), calls)
    }
}
