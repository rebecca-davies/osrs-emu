package emu.game.action

import emu.game.chat.ChatFilterInput
import emu.game.ui.ButtonClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncomingPlayerActionQueueTest {
    @Test
    fun `mixed actions retain network queue order`() {
        val queue = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig(capacity = 4, maxPerCycle = 4))
        val route = PlayerAction.Route(3222, 3218)
        val button = PlayerAction.Button(ButtonClick(160, 28))
        val chat = PlayerAction.Chat(ChatFilterInput(2, 1, 0))

        assertTrue(queue.submit(route))
        assertTrue(queue.submit(button))
        assertTrue(queue.submit(chat))

        val drained = mutableListOf<PlayerAction>()
        queue.drain(drained::add)

        assertEquals(listOf(route, button, chat), drained)
    }

    @Test
    fun `cycle budget leaves later actions ordered for the next cycle`() {
        val queue = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig(capacity = 4, maxPerCycle = 2))
        val actions: List<PlayerAction> =
            listOf(
                PlayerAction.Route(1, 1, false),
                PlayerAction.Route(2, 2, false),
                PlayerAction.Route(3, 3, false),
            )
        actions.forEach { assertTrue(queue.submit(it)) }

        val firstCycle = mutableListOf<PlayerAction>()
        val secondCycle = mutableListOf<PlayerAction>()
        queue.drain(firstCycle::add)
        queue.drain(secondCycle::add)

        assertEquals(actions.take(2), firstCycle)
        assertEquals(actions.takeLast(1), secondCycle)
    }

    @Test
    fun `full queue rejects newest action without disturbing accepted order`() {
        val queue = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig(capacity = 2, maxPerCycle = 2))
        val first: PlayerAction = PlayerAction.Route(1, 1, false)
        val second: PlayerAction = PlayerAction.Route(2, 2, false)

        assertTrue(queue.submit(first))
        assertTrue(queue.submit(second))
        assertFalse(queue.submit(PlayerAction.Route(3, 3, false)))

        val drained = mutableListOf<PlayerAction>()
        queue.drain(drained::add)
        assertEquals(listOf(first, second), drained)
    }

    @Test
    fun `route action rejects coordinates outside the game world`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerAction.Route(0x4000, 0)
        }
    }

    @Test
    fun `queue configuration requires positive capacity and cycle budget`() {
        assertFailsWith<IllegalArgumentException> { IncomingPlayerActionQueueConfig(capacity = 0, maxPerCycle = 1) }
        assertFailsWith<IllegalArgumentException> { IncomingPlayerActionQueueConfig(capacity = 1, maxPerCycle = 0) }
        assertFailsWith<IllegalArgumentException> { IncomingPlayerActionQueueConfig(capacity = 1, maxPerCycle = 2) }
    }
}
