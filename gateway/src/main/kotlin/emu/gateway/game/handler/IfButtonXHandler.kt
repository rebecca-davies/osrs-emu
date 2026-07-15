package emu.gateway.game.handler

import emu.game.ui.ButtonClick
import emu.game.ui.PlayerButtonSink
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.IfButtonX
import io.github.oshai.kotlinlogging.KotlinLogging

private val buttonLogger = KotlinLogging.logger {}

/** Converts the revision-specific packed component to a bounded, revision-neutral game input. */
class IfButtonXHandler(
    private val buttons: PlayerButtonSink,
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
        if (!buttons.submit(click)) {
            buttonLogger.warn { "button input queue saturated; rejecting click" }
        }
    }
}
