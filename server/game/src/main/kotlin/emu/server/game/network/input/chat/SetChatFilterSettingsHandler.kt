package emu.server.game.network.input.chat

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.chat.ChatFilterInput
import emu.protocol.osrs239.game.message.chat.SetChatFilterSettings
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Queues the complete client filter selection without mutating account state on gateway I/O. */
class SetChatFilterSettingsHandler(
    private val actions: PlayerActionSink,
) : MessageHandler<SetChatFilterSettings> {
    override suspend fun handle(message: SetChatFilterSettings, ctx: HandlerContext) {
        val input = ChatFilterInput(message.publicFilter, message.privateFilter, message.tradeFilter)
        if (!actions.submit(PlayerAction.Chat(input))) {
            throw IncomingPlayerActionQueueOverflow
        }
    }
}
