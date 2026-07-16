package emu.server.game.network.input.movement

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.message.movement.MoveGameClick
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/**
 * Queues a decoded click on the bounded network-to-world action queue.
 *
 * It deliberately performs no path search and mutates no player state: both happen later on the
 * authoritative world cycle's client-input/player phases.
 */
class MoveGameClickHandler(
    private val actions: PlayerActionSink,
) : MessageHandler<MoveGameClick> {
    override suspend fun handle(message: MoveGameClick, ctx: HandlerContext) {
        val action =
            PlayerAction.Route(
                x = message.x,
                y = message.z,
                invertRun = message.keyCombination == CONTROL_KEY,
            )
        if (!actions.submit(action)) {
            throw IncomingPlayerActionQueueOverflow
        }
    }

    private companion object {
        const val CONTROL_KEY = 1
    }
}
