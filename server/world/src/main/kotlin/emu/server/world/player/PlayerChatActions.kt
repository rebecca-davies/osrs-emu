package emu.server.world.player

import emu.compression.HuffmanCodec
import emu.game.chat.ChatFilterInput
import emu.game.chat.chatActions
import emu.game.varp.PlayerVarps
import emu.persistence.account.PlayerRank
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatChannel
import emu.protocol.osrs239.game.message.PlayerPublicChat
import java.time.Clock

/** Registers chat-filter and public-message actions for one player. */
internal fun playerChatActions(
    playerId: Long,
    rank: PlayerRank,
    varps: PlayerVarps,
    state: PlayerPublicChatState,
    audit: ChatAuditSink,
    huffman: HuffmanCodec,
    clock: Clock = Clock.systemUTC(),
) = chatActions {
    onFilterSettings { input -> applyChatFilters(varps, input) }
    onPublicMessage { input ->
        if (!state.canPublish()) return@onPublicMessage
        val auditMessage = ChatAuditMessage(playerId, ChatChannel.PUBLIC, input.text, clock.instant())
        if (!audit.submit(auditMessage)) return@onPublicMessage
        state.publish(
            PlayerPublicChat(
                colour = input.colour,
                effect = input.effect,
                modIcon = rank.id,
                encodedText = huffman.encode(input.text),
                pattern = input.pattern?.copyOf(),
            ),
        )
    }
}

private fun applyChatFilters(varps: PlayerVarps, input: ChatFilterInput) {
    if (input.publicFilter !in 0..3 || input.privateFilter !in 0..2 || input.tradeFilter !in 0..2) return
    varps[PlayerVarpCatalog.PUBLIC_CHAT_FILTER] = input.publicFilter
    varps[PlayerVarpCatalog.PRIVATE_CHAT_FILTER] = input.privateFilter
    varps[PlayerVarpCatalog.TRADE_CHAT_FILTER] = input.tradeFilter
}
