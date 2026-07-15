package emu.transport.pipeline

import emu.transport.message.IncomingMessage

/** Handles one decoded packet type. */
fun interface PacketHandler<in T : IncomingMessage> {
    suspend fun handle(message: T, ctx: HandlerContext)
}
