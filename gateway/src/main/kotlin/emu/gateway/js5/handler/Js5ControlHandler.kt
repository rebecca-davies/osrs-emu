package emu.gateway.js5.handler

import emu.crypto.XorStreamCipher
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.js5.message.Js5Control
import emu.protocol.osrs239.js5.prot.Js5Prot

/**
 * Consumes [Js5Control] frames silently so the JS5 group stream keeps flowing — the client
 * interleaves these with group requests after the handshake and expects no response (see
 * [Js5Control]'s doc for why the pipeline must still consume, not drop, them).
 *
 * Control opcode [Js5Prot.CONTROL_XOR_KEY] (4) sets this connection's response XOR key (its `b0`
 * byte) on [cipher] — the SAME [XorStreamCipher] instance the connection's `ProtocolStage` hands to
 * the response encoder, so a key set here is visible to every subsequent encoded response. That
 * instance is per-connection mutable state; it is threaded in via the constructor (plain
 * dependency injection, CLAUDE.md §5a/D) rather than through [HandlerContext], since one handler
 * instance is built per connection in `installJs5Handlers` (see `Main.kt`) instead of being shared
 * across connections like the codec/handler repositories are.
 */
class Js5ControlHandler(private val cipher: XorStreamCipher) : PacketHandler<Js5Control> {
    override suspend fun handle(message: Js5Control, ctx: HandlerContext) {
        if (message.opcode == Js5Prot.CONTROL_XOR_KEY) cipher.key = message.b0
    }
}
