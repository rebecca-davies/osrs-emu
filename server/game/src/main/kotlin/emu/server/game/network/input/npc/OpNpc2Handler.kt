package emu.server.game.network.input.npc

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.npc.NpcOpInput
import emu.protocol.osrs239.game.message.npc.OpNpc2
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Converts revision-specific `OPNPC2` data to one bounded NPC action. */
class OpNpc2Handler(
    private val actions: PlayerActionSink,
) : MessageHandler<OpNpc2> {
    override suspend fun handle(message: OpNpc2, ctx: HandlerContext) {
        val input =
            NpcOpInput(
                index = message.index,
                option = SECOND_OPTION,
                subOption = message.subOption,
                controlKey = message.controlKey,
            )
        if (!actions.submit(PlayerAction.NpcOp(input))) throw IncomingPlayerActionQueueOverflow
    }

    private companion object {
        const val SECOND_OPTION = 2
    }
}
