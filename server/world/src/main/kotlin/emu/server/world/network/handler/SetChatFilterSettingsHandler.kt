package emu.server.world.network.handler

import emu.game.chat.ChatFilterInput
import emu.game.input.PlayerInput
import emu.game.input.PlayerInputSink
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.SetChatFilterSettings
import io.github.oshai.kotlinlogging.KotlinLogging

private val chatFilterLogger = KotlinLogging.logger {}

/** Admits the complete client filter selection without mutating account state on gateway I/O. */
class SetChatFilterSettingsHandler(
    private val inputs: PlayerInputSink,
) : PacketHandler<SetChatFilterSettings> {
    override suspend fun handle(message: SetChatFilterSettings, ctx: HandlerContext) {
        val input = ChatFilterInput(message.publicFilter, message.privateFilter, message.tradeFilter)
        if (!inputs.submit(PlayerInput.Chat(input))) {
            chatFilterLogger.warn { "player input mailbox saturated; rejecting chat-filter update" }
        }
    }
}
