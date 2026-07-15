package emu.server.world.player

import emu.compression.HuffmanCodec
import emu.game.chat.ChatFilterInput
import emu.game.chat.ChatInput
import emu.game.chat.PublicChatInput
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatChannel
import emu.protocol.osrs239.game.message.PlayerPublicChat
import emu.server.world.entity.WorldPlayer
import emu.server.world.network.PlayerPublicChatState
import java.time.Clock

/** Applies validated chat actions to world-owned player state. */
class PlayerChatActionProcess(
    private val huffman: HuffmanCodec,
    private val audit: ChatAuditSink,
    private val clock: Clock = Clock.systemUTC(),
) {
    internal fun process(
        player: WorldPlayer,
        publicChat: PlayerPublicChatState,
        action: ChatInput,
    ) {
        when (action) {
            is ChatFilterInput -> applyFilters(player, action)
            is PublicChatInput -> publish(player, publicChat, action)
        }
    }

    private fun applyFilters(player: WorldPlayer, action: ChatFilterInput) {
        if (action.publicFilter !in 0..3 || action.privateFilter !in 0..2 || action.tradeFilter !in 0..2) return
        player.chatFilters.update(action.publicFilter, action.privateFilter, action.tradeFilter)
    }

    private fun publish(
        player: WorldPlayer,
        publicChat: PlayerPublicChatState,
        action: PublicChatInput,
    ) {
        if (!publicChat.canPublish()) return
        val auditMessage = ChatAuditMessage(player.id, ChatChannel.PUBLIC, action.text, clock.instant())
        if (!audit.submit(auditMessage)) return
        publicChat.publish(
            PlayerPublicChat(
                colour = action.colour,
                effect = action.effect,
                modIcon = player.rank.id,
                encodedText = huffman.encode(action.text),
                pattern = action.pattern,
            ),
        )
    }
}
