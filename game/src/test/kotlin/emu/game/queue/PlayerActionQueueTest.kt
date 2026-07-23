package emu.game.queue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlayerActionQueueTest {
    @Test
    fun `negative non-null delay is ready while the null sentinel is rejected`() {
        val calls = mutableListOf<String>()
        val queue = PlayerActionQueue<String>()
        queue.add("ready", delayTicks = -2)

        queue.processPrimaryAndWeak(canAccess = { true }, execute = calls::add)

        assertEquals(listOf("ready"), calls)
        assertFailsWith<IllegalArgumentException> { queue.add("null", delayTicks = -1) }
    }

    @Test
    fun `primary and weak delays count down while access is blocked`() {
        val calls = mutableListOf<String>()
        val queue = PlayerActionQueue<String>()
        queue.add("normal", PlayerActionPriority.NORMAL, delayTicks = 1)
        queue.add("weak", PlayerActionPriority.WEAK)

        queue.processPrimaryAndWeak(canAccess = { false }, execute = calls::add)
        assertEquals(emptyList(), calls)
        queue.processPrimaryAndWeak(canAccess = { true }, execute = calls::add)

        assertEquals(listOf("normal", "weak"), calls)
    }

    @Test
    fun `strong action closes the modal and clears weak actions before its delay expires`() {
        val calls = mutableListOf<String>()
        var modalOpen = true
        val queue = PlayerActionQueue<String>()
        queue.add("weak", PlayerActionPriority.WEAK)
        queue.add("strong", PlayerActionPriority.STRONG, delayTicks = 5)

        queue.processPrimaryAndWeak(
            canAccess = { !modalOpen },
            closeModal = {
                calls += "close"
                modalOpen = false
            },
            execute = calls::add,
        )

        assertEquals(listOf("close"), calls)
        assertEquals(0, queue.weakSize)
        assertEquals(1, queue.primarySize)
    }

    @Test
    fun `access is checked again after every ready action`() {
        val calls = mutableListOf<String>()
        var accessible = true
        val queue = PlayerActionQueue<String>()
        queue.add("first")
        queue.add("second")

        queue.processPrimaryAndWeak(
            canAccess = { accessible },
            execute = {
                calls += it
                accessible = false
            },
        )

        assertEquals(listOf("first"), calls)
        assertEquals(1, queue.primarySize)
    }

    @Test
    fun `engine actions force zero delay but still wait for access`() {
        val calls = mutableListOf<String>()
        val queue = PlayerActionQueue<String>()
        queue.add("engine", PlayerActionPriority.ENGINE, delayTicks = 50)

        queue.processEngine(canAccess = { false }, execute = calls::add)
        assertEquals(emptyList(), calls)
        queue.processEngine(canAccess = { true }, execute = calls::add)

        assertEquals(listOf("engine"), calls)
    }

    @Test
    fun `failing engine action remains queued until it completes successfully`() {
        val calls = mutableListOf<String>()
        val queue = PlayerActionQueue<String>()
        queue.add("engine", PlayerActionPriority.ENGINE)

        assertFailsWith<IllegalStateException> {
            queue.processEngine(canAccess = { true }) {
                calls += it
                error("failed")
            }
        }
        assertEquals(1, queue.engineSize)

        queue.processEngine(canAccess = { true }, execute = calls::add)

        assertEquals(listOf("engine", "engine"), calls)
        assertEquals(0, queue.engineSize)
    }

    @Test
    fun `engine mutation observes execute then unlink ordering`() {
        val queue = PlayerActionQueue<String>()
        queue.add("engine", PlayerActionPriority.ENGINE)
        var sizeDuringExecution = 0

        queue.processEngine(canAccess = { true }) {
            sizeDuringExecution = queue.engineSize
        }

        assertEquals(1, sizeDuringExecution)
        assertEquals(0, queue.engineSize)
    }

    @Test
    fun `linked traversal preserves the same-cycle append quirk`() {
        val calls = mutableListOf<String>()
        val queue = PlayerActionQueue<String>()
        queue.add("first")
        queue.add("tail", delayTicks = 9)

        queue.processPrimaryAndWeak(
            canAccess = { true },
            execute = {
                calls += it
                if (it == "first") queue.add("appended")
            },
        )

        assertEquals(listOf("first", "appended"), calls)
    }

    @Test
    fun `an action appended behind the sole entry waits until the next cycle`() {
        val calls = mutableListOf<String>()
        val queue = PlayerActionQueue<String>()
        queue.add("first")

        queue.processPrimaryAndWeak(
            canAccess = { true },
            execute = {
                calls += it
                if (it == "first") queue.add("appended")
            },
        )
        assertEquals(listOf("first"), calls)

        queue.processPrimaryAndWeak(canAccess = { true }, execute = calls::add)
        assertEquals(listOf("first", "appended"), calls)
    }

    @Test
    fun `long actions expose the two authentic logout behaviours`() {
        val calls = mutableListOf<String>()
        val queue = PlayerActionQueue<String>()
        queue.addLong("discard", delayTicks = 10, logout = LongActionLogout.DISCARD)
        assertTrue(queue.isDiscardableForLogout())

        queue.add("normal")
        assertFalse(queue.isDiscardableForLogout())
        queue.clearPrimary()

        queue.addLong("accelerate", delayTicks = 10, logout = LongActionLogout.ACCELERATE)
        queue.processPrimaryAndWeak(
            canAccess = { true },
            loggingOut = true,
            execute = calls::add,
        )

        assertEquals(listOf("accelerate"), calls)
    }

    @Test
    fun `action removal scans primary and weak but keeps engine work separate`() {
        val queue = PlayerActionQueue<String>()
        queue.add("same")
        queue.add("same", PlayerActionPriority.WEAK)
        queue.add("same", PlayerActionPriority.ENGINE)

        assertEquals(2, queue.count("same"))
        assertEquals(2, queue.removeAll("same"))
        assertEquals(0, queue.count("same"))
        assertEquals(1, queue.engineSize)

        assertEquals(1, queue.removeAllEngine("same"))
        assertEquals(0, queue.engineSize)
    }

    @Test
    fun `full player queue rejects newest work across all lanes without changing order`() {
        val queue = PlayerActionQueue<String>(capacity = 3)
        assertTrue(queue.add("normal"))
        assertTrue(queue.add("weak", PlayerActionPriority.WEAK))
        assertTrue(queue.add("engine", PlayerActionPriority.ENGINE))

        assertFalse(queue.addLong("rejected", delayTicks = 0, logout = LongActionLogout.DISCARD))
        assertEquals(3, queue.size)

        val calls = mutableListOf<String>()
        queue.processPrimaryAndWeak(canAccess = { true }, execute = calls::add)
        queue.processEngine(canAccess = { true }, execute = calls::add)
        assertEquals(listOf("normal", "weak", "engine"), calls)
    }
}
