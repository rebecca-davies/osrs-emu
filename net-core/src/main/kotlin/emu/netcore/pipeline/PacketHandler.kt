package emu.netcore.pipeline

import emu.netcore.message.IncomingMessage

/** Handles one decoded packet type. */
fun interface PacketHandler<in T : IncomingMessage> {
    suspend fun handle(message: T, ctx: HandlerContext)
}
