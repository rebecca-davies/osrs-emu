package emu.server.game.network.input.chat

import emu.compression.HuffmanCodec
import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.chat.PublicChatInput
import emu.protocol.osrs239.game.message.chat.MessagePublic
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Decodes and validates public text before queuing it as a bounded action. */
class MessagePublicHandler(
    private val huffman: HuffmanCodec,
    private val actions: PlayerActionSink,
) : MessageHandler<MessagePublic> {
    override suspend fun handle(message: MessagePublic, ctx: HandlerContext) {
        if (message.type != PUBLIC_CHANNEL_TYPE) return
        val text = normalizeChatText(huffman.decode(message.encodedText)) ?: return
        val input = PublicChatInput(message.colour, message.effect, text, message.pattern)
        if (!actions.submit(PlayerAction.Chat(input))) {
            throw IncomingPlayerActionQueueOverflow
        }
    }

    private fun normalizeChatText(text: String): String? {
        if (text.any(Character::isISOControl)) return null
        val normalized = text.replace(WHITESPACE, " ").trim()
        return normalized.takeIf { it.isNotEmpty() && it.length <= PublicChatInput.MAX_CHAT_LENGTH }
    }

    private companion object {
        const val PUBLIC_CHANNEL_TYPE = 0
        val WHITESPACE = Regex("[\\s\\u00a0]+")
    }
}
