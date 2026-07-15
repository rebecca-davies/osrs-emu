package emu.transport.pipeline

import emu.transport.message.OutgoingMessage

/** Packet-handler output boundary backed by [ProtocolStage]'s codec and cipher state. */
interface HandlerContext {
    suspend fun write(message: OutgoingMessage)
}
