package emu.server.world.network.handler

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.MoveGameClick

/**
 * Queues a decoded click on the bounded network-to-world action queue.
 *
 * It deliberately performs no path search and mutates no player state: both happen later on the
 * authoritative world cycle's client-input/player phases.
 */
class MoveGameClickHandler(
    private val actions: PlayerActionSink,
) : PacketHandler<MoveGameClick> {
    override suspend fun handle(message: MoveGameClick, ctx: HandlerContext) {
        val action =
            PlayerAction.Route(
                x = message.x,
                y = message.z,
                invertRun = message.keyCombination == CONTROL_KEY,
            )
        if (!actions.submit(action)) {
            throw GameInputQueueOverflow
        }
    }

    private companion object {
        const val CONTROL_KEY = 1
    }
}
