package emu.server.world.network.handler

import emu.game.chat.ChatFilterInput
import emu.game.chat.PlayerChatSink
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.SetChatFilterSettings
import io.github.oshai.kotlinlogging.KotlinLogging

private val chatFilterLogger = KotlinLogging.logger {}

/** Admits the complete client filter selection without mutating account state on gateway I/O. */
class SetChatFilterSettingsHandler(
    private val chat: PlayerChatSink,
) : PacketHandler<SetChatFilterSettings> {
    override suspend fun handle(message: SetChatFilterSettings, ctx: HandlerContext) {
        val input = ChatFilterInput(message.publicFilter, message.privateFilter, message.tradeFilter)
        if (!chat.submit(input)) {
            chatFilterLogger.warn { "chat input queue saturated; rejecting filter update" }
        }
    }
}
