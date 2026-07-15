package emu.game.chat

import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerChatQueueTest {
    @Test fun `bounded mailbox dispatches accepted inputs on the cycle`() {
        val queue = PlayerChatQueue(capacity = 1, maxPerCycle = 1)
        val handled = mutableListOf<ChatInput>()
        val actions = chatActions {
            onPublicMessage { handled += it }
            onFilterSettings { handled += it }
        }
        val message = PublicChatInput(0, 0, "hello", null)

        assertTrue(queue.submit(message))
        assertFalse(queue.submit(ChatFilterInput(0, 0, 0)))
        runSuspending { queue.cycleProcesses(actions).single().process(0) }

        assertEquals(listOf<ChatInput>(message), handled)
    }
}
