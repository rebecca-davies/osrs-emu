package emu.server.game.player

import emu.compression.HuffmanCodec
import emu.game.chat.ChatFilterInput
import emu.game.chat.PublicChatInput
import emu.game.chat.chatActions
import emu.game.varp.PlayerVarps
import emu.persistence.account.PlayerRank
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatChannel
import emu.protocol.osrs239.game.message.PlayerPublicChat
import java.time.Clock

/** One local player's pending public-chat update, consumed once by PLAYER_INFO. */
internal class PlayerChatState {
    private var publicChat: PlayerPublicChat? = null

    fun canPublish(): Boolean = publicChat == null

    fun publish(message: PlayerPublicChat) {
        check(publicChat == null) { "only one public message may be published per player cycle" }
        publicChat = message
    }

    fun takePublicChat(): PlayerPublicChat? = publicChat.also { publicChat = null }
}

/** Game-cycle chat content: validate filters, gate public chat on audit admission, then publish. */
internal fun playerChatActions(
    playerId: Long,
    rank: PlayerRank,
    varps: PlayerVarps,
    state: PlayerChatState,
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
    varps[PlayerVarpTypes.PUBLIC_CHAT_FILTER] = input.publicFilter
    varps[PlayerVarpTypes.PRIVATE_CHAT_FILTER] = input.privateFilter
    varps[PlayerVarpTypes.TRADE_CHAT_FILTER] = input.tradeFilter
}
