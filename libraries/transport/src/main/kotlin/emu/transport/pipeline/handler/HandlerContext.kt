package emu.transport.pipeline.handler

import emu.transport.message.OutgoingMessage

/** Message-handler output boundary backed by [ProtocolStage]'s codec and cipher state. */
interface HandlerContext {
    suspend fun write(message: OutgoingMessage)
}
