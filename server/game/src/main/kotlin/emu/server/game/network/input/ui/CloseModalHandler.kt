package emu.server.game.network.input.ui

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.message.component.CloseModal
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Queues a modal close request for the authoritative player queue point. */
class CloseModalHandler(
    private val actions: PlayerActionSink,
) : MessageHandler<CloseModal> {
    override suspend fun handle(message: CloseModal, ctx: HandlerContext) {
        if (!actions.submit(PlayerAction.CloseModal)) {
            throw IncomingPlayerActionQueueOverflow
        }
    }
}
