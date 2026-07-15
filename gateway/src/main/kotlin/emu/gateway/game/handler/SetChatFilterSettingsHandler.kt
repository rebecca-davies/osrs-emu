package emu.gateway.game.handler

import emu.game.chat.ChatFilterInput
import emu.game.chat.PlayerChatSink
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.SetChatFilterSettings

/** Admits the complete client filter selection without mutating account state on gateway I/O. */
class SetChatFilterSettingsHandler(
    private val chat: PlayerChatSink,
) : PacketHandler<SetChatFilterSettings> {
    override suspend fun handle(message: SetChatFilterSettings, ctx: HandlerContext) {
        chat.submit(ChatFilterInput(message.publicFilter, message.privateFilter, message.tradeFilter))
    }
}
