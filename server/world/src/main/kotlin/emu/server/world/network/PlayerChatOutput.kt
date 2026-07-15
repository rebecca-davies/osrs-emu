package emu.server.world.network

import emu.game.player.PlayerChatFilters
import emu.protocol.osrs239.game.message.ChatFilterPrivate
import emu.protocol.osrs239.game.message.ChatFilterSettings
import emu.transport.message.OutgoingMessage

/** Converts authoritative chat visibility modes into revision-239 messages. */
internal object PlayerChatOutput {
    fun messages(filters: PlayerChatFilters): List<OutgoingMessage> =
        listOf(
            ChatFilterSettings(
                publicFilter = filters.publicMode,
                tradeFilter = filters.tradeMode,
            ),
            ChatFilterPrivate(filters.privateMode),
        )
}
