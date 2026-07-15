package emu.server.world.network.handler

import emu.game.chat.ChatFilterInput
import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.SetChatFilterSettings

/** Queues the complete client filter selection without mutating account state on gateway I/O. */
class SetChatFilterSettingsHandler(
    private val actions: PlayerActionSink,
) : PacketHandler<SetChatFilterSettings> {
    override suspend fun handle(message: SetChatFilterSettings, ctx: HandlerContext) {
        val input = ChatFilterInput(message.publicFilter, message.privateFilter, message.tradeFilter)
        if (!actions.submit(PlayerAction.Chat(input))) {
            throw GameInputQueueOverflow
        }
    }
}
