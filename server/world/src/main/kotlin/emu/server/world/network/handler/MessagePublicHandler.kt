package emu.server.world.network.handler

import emu.compression.HuffmanCodec
import emu.game.chat.PublicChatInput
import emu.game.input.PlayerInput
import emu.game.input.PlayerInputSink
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.MessagePublic
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Decodes and validates public text before admitting it to the bounded cycle mailbox. */
class MessagePublicHandler(
    private val huffman: HuffmanCodec,
    private val inputs: PlayerInputSink,
) : PacketHandler<MessagePublic> {
    override suspend fun handle(message: MessagePublic, ctx: HandlerContext) {
        if (message.type != PUBLIC_CHANNEL_TYPE) return
        val text =
            try {
                normalizeChatText(huffman.decode(message.encodedText)) ?: return
            } catch (_: IllegalArgumentException) {
                logger.warn { "rejected malformed public chat payload" }
                return
            }
        val input =
            try {
                PublicChatInput(message.colour, message.effect, text, message.pattern?.copyOf())
            } catch (_: IllegalArgumentException) {
                logger.warn { "rejected invalid public chat metadata" }
                return
            }
        if (!inputs.submit(PlayerInput.Chat(input))) {
            logger.warn { "player input mailbox saturated; rejecting public chat message" }
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
