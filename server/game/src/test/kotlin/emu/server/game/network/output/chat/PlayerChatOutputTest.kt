package emu.server.game.network.output.chat

import emu.game.player.PlayerChatFilters
import emu.protocol.osrs239.game.message.chat.ChatFilterPrivate
import emu.protocol.osrs239.game.message.chat.ChatFilterSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerChatOutputTest {
    @Test
    fun `persisted chat settings use their dedicated filter packets`() {
        val filters = PlayerChatFilters(publicMode = 3, privateMode = 1, tradeMode = 2)

        assertEquals(
            listOf(ChatFilterSettings(3, 2), ChatFilterPrivate(1)),
            PlayerChatOutput.messages(filters),
        )
    }
}
