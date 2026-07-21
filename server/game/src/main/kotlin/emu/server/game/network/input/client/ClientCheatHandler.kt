package emu.server.game.network.input.client

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.cheat.PlayerCheatInput
import emu.protocol.osrs239.game.message.client.ClientCheat
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Queues developer-console input without running command logic on connection IO. */
class ClientCheatHandler(
    private val actions: PlayerActionSink,
) : MessageHandler<ClientCheat> {
    override suspend fun handle(message: ClientCheat, ctx: HandlerContext) {
        if (!actions.submit(PlayerAction.Cheat(PlayerCheatInput(message.input)))) {
            throw IncomingPlayerActionQueueOverflow
        }
    }
}
