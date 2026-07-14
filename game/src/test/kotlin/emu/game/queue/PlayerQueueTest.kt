package emu.game.queue

import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerQueueTest {
    @Test
    fun `primary and weak delays count down while access is blocked`() {
        val calls = mutableListOf<String>()
        val queues = PlayerQueues()
        queues.enqueue(PlayerQueueType.NORMAL, delayTicks = 1) { calls += "normal" }
        queues.enqueue(PlayerQueueType.WEAK, delayTicks = 0) { calls += "weak" }

        runSuspending { queues.processPrimaryAndWeak(canAccess = { false }) }
        assertEquals(emptyList(), calls)
        runSuspending { queues.processPrimaryAndWeak(canAccess = { true }) }

        assertEquals(listOf("normal", "weak"), calls)
    }

    @Test
    fun `strong queue closes modal and clears weak queue before delay expires`() {
        val calls = mutableListOf<String>()
        var modalOpen = true
        val queues = PlayerQueues()
        queues.enqueue(PlayerQueueType.WEAK, delayTicks = 0) { calls += "weak" }
        queues.enqueue(PlayerQueueType.STRONG, delayTicks = 5) { calls += "strong" }

        runSuspending {
            queues.processPrimaryAndWeak(
                canAccess = { !modalOpen },
                closeModal = {
                    calls += "close"
                    modalOpen = false
                },
            )
        }

        assertEquals(listOf("close"), calls)
        assertEquals(0, queues.weakSize)
        assertEquals(1, queues.primarySize)
    }

    @Test
    fun `engine actions force zero delay but still wait for access`() {
        val calls = mutableListOf<String>()
        val queues = PlayerQueues()
        queues.enqueueEngine { calls += "engine" }

        runSuspending { queues.processEngine(canAccess = { false }) }
        assertEquals(emptyList(), calls)
        runSuspending { queues.processEngine(canAccess = { true }) }

        assertEquals(listOf("engine"), calls)
    }

    @Test
    fun `linked queue preserves authentic same-cycle append quirk`() {
        val calls = mutableListOf<String>()
        val queues = PlayerQueues()
        queues.enqueue(PlayerQueueType.NORMAL, 0) {
            calls += "first"
            queues.enqueue(PlayerQueueType.NORMAL, 0) { calls += "appended" }
        }
        queues.enqueue(PlayerQueueType.NORMAL, 9) { calls += "tail" }

        runSuspending { queues.processPrimaryAndWeak(canAccess = { true }) }

        assertEquals(listOf("first", "appended"), calls)
    }

    @Test
    fun `append from sole queue entry waits until next cycle`() {
        val calls = mutableListOf<String>()
        val queues = PlayerQueues()
        queues.enqueue(PlayerQueueType.NORMAL, 0) {
            calls += "first"
            queues.enqueue(PlayerQueueType.NORMAL, 0) { calls += "appended" }
        }

        runSuspending { queues.processPrimaryAndWeak(canAccess = { true }) }
        assertEquals(listOf("first"), calls)
        runSuspending { queues.processPrimaryAndWeak(canAccess = { true }) }

        assertEquals(listOf("first", "appended"), calls)
    }

    @Test
    fun `long queue exposes authentic logout policies`() {
        val queues = PlayerQueues()
        queues.enqueueLong(delayTicks = 10, logoutPolicy = LongQueueLogoutPolicy.DISCARD) {}
        assertTrue(queues.isDiscardableForLogout())

        queues.enqueue(PlayerQueueType.NORMAL, 0) {}
        assertFalse(queues.isDiscardableForLogout())
        queues.clearPrimary()

        var accelerated = false
        queues.enqueueLong(delayTicks = 10, logoutPolicy = LongQueueLogoutPolicy.ACCELERATE) {
            accelerated = true
        }
        runSuspending {
            queues.processPrimaryAndWeak(canAccess = { true }, loggingOut = true)
        }
        assertTrue(accelerated)
    }
}
