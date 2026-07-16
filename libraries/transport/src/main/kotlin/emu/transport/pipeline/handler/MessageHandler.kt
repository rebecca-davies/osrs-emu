package emu.transport.pipeline.handler

import emu.transport.message.IncomingMessage

/** Handles one decoded incoming message type. */
fun interface MessageHandler<in T : IncomingMessage> {
    suspend fun handle(message: T, ctx: HandlerContext)
}
