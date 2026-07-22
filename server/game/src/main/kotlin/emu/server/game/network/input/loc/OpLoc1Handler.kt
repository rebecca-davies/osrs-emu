package emu.server.game.network.input.loc

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.loc.LocOpInput
import emu.protocol.osrs239.game.message.loc.OpLoc1
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Converts revision-specific `OPLOC1` data to one bounded loc action. */
class OpLoc1Handler(
    private val actions: PlayerActionSink,
) : MessageHandler<OpLoc1> {
    override suspend fun handle(message: OpLoc1, ctx: HandlerContext) {
        val input =
            LocOpInput(
                type = message.type,
                x = message.x,
                z = message.z,
                option = FIRST_OPTION,
                subOption = message.subOption,
                controlKey = message.keyCombination == CONTROL_KEY,
            )
        if (!actions.submit(PlayerAction.LocOp(input))) throw IncomingPlayerActionQueueOverflow
    }

    private companion object {
        const val FIRST_OPTION = 1
        const val CONTROL_KEY = 1
    }
}
