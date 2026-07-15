package emu.server.world.network.handler

import emu.game.input.PlayerInput
import emu.game.input.PlayerInputSink
import emu.game.ui.ButtonClick
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.IfButtonX
import io.github.oshai.kotlinlogging.KotlinLogging

private val buttonLogger = KotlinLogging.logger {}

/** Converts the revision-specific packed component to a bounded, revision-neutral game input. */
class IfButtonXHandler(
    private val inputs: PlayerInputSink,
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
        if (!inputs.submit(PlayerInput.Button(click))) {
            buttonLogger.warn { "player input mailbox saturated; rejecting click" }
        }
    }
}
