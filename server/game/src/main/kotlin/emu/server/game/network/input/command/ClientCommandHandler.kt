package emu.server.game.network.input.command

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.command.PlayerCommandInput
import emu.protocol.osrs239.game.message.client.ClientCheat
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Converts Jagex `CLIENT_CHEAT` input into an ordered player command action. */
class ClientCommandHandler(
    private val actions: PlayerActionSink,
) : MessageHandler<ClientCheat> {
    override suspend fun handle(message: ClientCheat, ctx: HandlerContext) {
        if (!actions.submit(PlayerAction.Command(PlayerCommandInput(message.input)))) {
            throw IncomingPlayerActionQueueOverflow
        }
    }
}
