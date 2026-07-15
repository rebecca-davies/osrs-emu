package emu.server.world.network.handler

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.ui.ButtonClick
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.IfButtonX

/** Converts a revision-specific packed component to a bounded, revision-neutral game action. */
class IfButtonXHandler(
    private val actions: PlayerActionSink,
) : PacketHandler<IfButtonX> {
    override suspend fun handle(message: IfButtonX, ctx: HandlerContext) {
        if (message.op !in 1..10) return
        val click =
            ButtonClick(
                interfaceId = message.combinedId ushr 16,
                componentId = message.combinedId and 0xFFFF,
                sub = message.sub,
                obj = message.obj,
                op = message.op,
        )
        if (!actions.submit(PlayerAction.Button(click))) {
            throw GameInputQueueOverflow
        }
    }
}
