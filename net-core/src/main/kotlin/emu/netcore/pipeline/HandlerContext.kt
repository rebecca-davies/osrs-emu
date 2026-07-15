package emu.netcore.pipeline

import emu.netcore.message.OutgoingMessage

/** Packet-handler output boundary backed by [ProtocolStage]'s codec and cipher state. */
interface HandlerContext {
    suspend fun write(message: OutgoingMessage)
}
