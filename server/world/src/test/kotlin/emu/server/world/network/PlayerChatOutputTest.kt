package emu.server.world.network

import emu.game.player.PlayerChatFilters
import emu.protocol.osrs239.game.message.ChatFilterPrivate
import emu.protocol.osrs239.game.message.ChatFilterSettings
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
