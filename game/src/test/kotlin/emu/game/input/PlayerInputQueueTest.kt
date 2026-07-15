package emu.game.input

import emu.game.chat.ChatFilterInput
import emu.game.ui.ButtonClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlayerInputQueueTest {
    @Test
    fun `mixed inputs retain network admission order`() {
        val queue = PlayerInputQueue(PlayerInputQueueConfig(capacity = 4, maxPerCycle = 4))
        val route = PlayerInput.Route(3222, 3218, invertRun = false)
        val button = PlayerInput.Button(ButtonClick(160, 28, -1, -1, 1))
        val chat = PlayerInput.Chat(ChatFilterInput(2, 1, 0))

        assertTrue(queue.submit(route))
        assertTrue(queue.submit(button))
        assertTrue(queue.submit(chat))

        val drained = mutableListOf<PlayerInput>()
        queue.drain(drained::add)

        assertEquals(listOf(route, button, chat), drained)
    }

    @Test
    fun `cycle budget leaves later inputs ordered for the next cycle`() {
        val queue = PlayerInputQueue(PlayerInputQueueConfig(capacity = 4, maxPerCycle = 2))
        val inputs: List<PlayerInput> =
            listOf(
                PlayerInput.Route(1, 1, false),
                PlayerInput.Route(2, 2, false),
                PlayerInput.Route(3, 3, false),
            )
        inputs.forEach { assertTrue(queue.submit(it)) }

        val firstCycle = mutableListOf<PlayerInput>()
        val secondCycle = mutableListOf<PlayerInput>()
        queue.drain(firstCycle::add)
        queue.drain(secondCycle::add)

        assertEquals(inputs.take(2), firstCycle)
        assertEquals(inputs.takeLast(1), secondCycle)
    }

    @Test
    fun `full mailbox rejects newest input without disturbing admitted order`() {
        val queue = PlayerInputQueue(PlayerInputQueueConfig(capacity = 2, maxPerCycle = 2))
        val first: PlayerInput = PlayerInput.Route(1, 1, false)
        val second: PlayerInput = PlayerInput.Route(2, 2, false)

        assertTrue(queue.submit(first))
        assertTrue(queue.submit(second))
        assertFalse(queue.submit(PlayerInput.Route(3, 3, false)))

        val drained = mutableListOf<PlayerInput>()
        queue.drain(drained::add)
        assertEquals(listOf(first, second), drained)
    }

    @Test
    fun `route intent rejects coordinates outside the game world`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerInput.Route(0x4000, 0, invertRun = false)
        }
    }

    @Test
    fun `queue configuration requires positive capacity and cycle budget`() {
        assertFailsWith<IllegalArgumentException> { PlayerInputQueueConfig(capacity = 0, maxPerCycle = 1) }
        assertFailsWith<IllegalArgumentException> { PlayerInputQueueConfig(capacity = 1, maxPerCycle = 0) }
    }
}
