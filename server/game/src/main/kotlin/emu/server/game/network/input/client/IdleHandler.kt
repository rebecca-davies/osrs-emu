package emu.server.game.network.input.client

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.message.client.Idle
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Queues an idle logout request for the authoritative world cycle. */
class IdleHandler(
    private val actions: PlayerActionSink,
) : MessageHandler<Idle> {
    override suspend fun handle(message: Idle, ctx: HandlerContext) {
        if (!actions.submit(PlayerAction.IdleLogout)) {
            throw IncomingPlayerActionQueueOverflow
        }
    }
}
