package emu.server.world.network.handler

import emu.game.input.PlayerInput
import emu.game.input.PlayerInputSink
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.MoveGameClick
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Admits a decoded click to the bounded network-to-world mailbox.
 *
 * It deliberately performs no path search and mutates no player state: both happen later on the
 * authoritative world cycle's client-input/player phases.
 */
class MoveGameClickHandler(
    private val inputs: PlayerInputSink,
) : PacketHandler<MoveGameClick> {
    override suspend fun handle(message: MoveGameClick, ctx: HandlerContext) {
        val intent =
            try {
                PlayerInput.Route(
                    x = message.x,
                    y = message.z,
                    invertRun = message.keyCombination == CONTROL_KEY,
                )
            } catch (_: IllegalArgumentException) {
                logger.warn { "rejected player route request outside world bounds" }
                return
            }
        if (!inputs.submit(intent)) {
            logger.warn { "player input mailbox saturated; rejecting route request" }
        }
    }

    private companion object {
        const val CONTROL_KEY = 1
    }
}
