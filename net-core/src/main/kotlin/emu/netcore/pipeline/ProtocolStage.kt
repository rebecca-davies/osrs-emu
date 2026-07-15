package emu.netcore.pipeline

import emu.buffer.JagexBuffer
import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.netcore.codec.CodecRepository
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/**
 * The thin composition point that replaces god-object "session" classes: a session is just
 * `ProtocolStage(codecs, handlers)`. Reads one opcode-framed message at a time, decodes it, routes
 * it through the type-keyed [handlers] repository, and encodes/writes whatever messages a handler
 * emits via [HandlerContext.write].
 *
 * [readOpcode]/[readPayload] keep this stage agnostic to how a protocol frames its
 * opcode/length (e.g. JS5's fixed 4-byte requests vs game var-byte packets), so the same
 * stage drives every protocol.
 *
 * [findProt] normally resolves through the decoder registry, preserving fail-closed behavior for
 * an unknown opcode. A revision may instead supply its complete opcode/size table: packets that
 * have a declared [Prot] but no implemented decoder are then framed and discarded, keeping the
 * stream cipher aligned without forcing placeholder message types into the registry.
 */
class ProtocolStage(
    private val codecs: CodecRepository,
    private val handlers: HandlerRepository,
    private val cipher: StreamCipher = NopStreamCipher,
    private val readOpcode: suspend (ByteReadChannel) -> Int,
    private val readPayload: suspend (ByteReadChannel, Prot) -> ByteArray,
    private val writeOpcode: Boolean = true,
    private val findProt: (Int) -> Prot? = { codecs.decoder(it)?.prot },
) {
    suspend fun run(read: ByteReadChannel, write: ByteWriteChannel) {
        run(read) { message -> emit(message, write) }
    }

    /**
     * Runs this stage with an injected outbound boundary. Game sessions use this overload to put
     * handler replies into their per-connection mailbox, keeping the reader away from the socket's
     * writer and outbound ISAAC state.
     */
    suspend fun run(read: ByteReadChannel, emit: suspend (OutgoingMessage) -> Unit) {
        val ctx = object : HandlerContext {
            override suspend fun write(message: OutgoingMessage) = emit(message)
        }
        while (true) {
            val opcode = readOpcode(read)
            val prot = findProt(opcode) ?: return
            val payload = readPayload(read, prot)
            val decoder = codecs.decoder(opcode) ?: continue
            val message = decoder.decode(JagexBuffer(payload))
            handlers.dispatch(message, ctx)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun emit(message: OutgoingMessage, write: ByteWriteChannel) {
        val encoder = codecs.encoder(message.javaClass) as? MessageEncoder<OutgoingMessage>
            ?: error("no encoder for ${message.javaClass}")
        writePacket(write, encoder, message, cipher, writeOpcode)
    }
}
