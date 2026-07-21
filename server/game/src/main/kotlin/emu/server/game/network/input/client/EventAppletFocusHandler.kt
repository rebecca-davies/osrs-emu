package emu.server.game.network.input.client

import emu.protocol.osrs239.game.message.client.EventAppletFocus
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Consumes client focus telemetry without creating world work. */
object EventAppletFocusHandler : MessageHandler<EventAppletFocus> {
    override suspend fun handle(message: EventAppletFocus, ctx: HandlerContext) = Unit
}
