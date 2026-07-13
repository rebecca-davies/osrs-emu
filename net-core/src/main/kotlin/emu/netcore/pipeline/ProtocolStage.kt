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
 */
class ProtocolStage(
    private val codecs: CodecRepository,
    private val handlers: HandlerRepository,
    private val cipher: StreamCipher = NopStreamCipher,
    private val readOpcode: suspend (ByteReadChannel) -> Int,
    private val readPayload: suspend (ByteReadChannel, Prot) -> ByteArray,
    private val writeOpcode: Boolean = true,
) {
    suspend fun run(read: ByteReadChannel, write: ByteWriteChannel) {
        val ctx = object : HandlerContext {
            override suspend fun write(message: OutgoingMessage) = emit(message, write)
        }
        while (true) {
            val opcode = readOpcode(read)
            val decoder = codecs.decoder(opcode) ?: return // unknown opcode: close
            val payload = readPayload(read, decoder.prot)
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
