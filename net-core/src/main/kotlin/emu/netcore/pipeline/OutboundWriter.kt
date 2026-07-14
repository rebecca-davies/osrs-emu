package emu.netcore.pipeline

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully

/**
 * Writes one outbound packet to [write]: encode the body, optionally prefix an opcode byte, flush.
 * The single reusable outbound-write path for every protocol stage — extracted out of
 * [ProtocolStage] so it can be unit-tested (and reused) independently of a running pipeline.
 *
 * Keystream ordering (read this before changing either side): [message] is encoded first via
 * [MessageEncoder.encode], passed the real [cipher] — this preserves encoders that consume the
 * cipher themselves to obfuscate their own body bytes (e.g. `Js5ResponseEncoder`'s per-byte XOR).
 * Only *after* that call, if [writeOpcode] is set, is a single further `cipher.nextInt()` drawn to
 * ISAAC-adjust the opcode byte: `(opcode + cipher.nextInt()) and 0xFF` — this is what rev-239
 * game packets do (the client computes `opcode = (rawByte - outboundIsaac.nextInt()) and 0xFF`,
 * so the server must add the same keystream int it consumed). The payload itself is never touched
 * by this opcode step. Net effect per protocol:
 *  - JS5 (`writeOpcode = false`): no opcode byte, so no extra `nextInt()` call is ever made here;
 *    the body's own XOR (via the real cipher passed into `encode`) is exactly as before.
 *  - Game packets (`writeOpcode = true`, real ISAAC cipher, bodies not cipher-consuming): body is
 *    written in the clear, opcode is ISAAC-scrambled by the one `nextInt()` this function draws.
 *  - Everything under [emu.crypto.NopStreamCipher] (`nextInt() == 0`): opcode arithmetic is a
 *    no-op, so wire output is byte-identical to the pre-ISAAC `encoder.prot.opcode.toByte()`.
 *
 * Length framing (rev-239): only when [writeOpcode] is set does a variable-size prot emit a
 * **plaintext** length between the (ISAAC-adjusted) opcode byte and the body — one u8 for
 * [Prot.VAR_BYTE], one big-endian u16 for [Prot.VAR_SHORT]; a fixed-size prot emits no length. The
 * length is never touched by the cipher (the client reads it in the clear to know how many body
 * bytes follow). JS5 (`writeOpcode = false`) skips this entirely, so its wire output is unchanged.
 */
suspend fun <T : OutgoingMessage> writePacket(
    write: ByteWriteChannel,
    encoder: MessageEncoder<T>,
    message: T,
    cipher: StreamCipher,
    writeOpcode: Boolean,
) {
    val body = encoder.encode(cipher, message)
    if (writeOpcode) {
        write.writeByte(((encoder.prot.opcode + cipher.nextInt()) and 0xFF).toByte())
        when (encoder.prot.size) {
            Prot.VAR_BYTE -> write.writeByte((body.size and 0xFF).toByte())
            Prot.VAR_SHORT -> {
                write.writeByte((body.size ushr 8).toByte())
                write.writeByte((body.size and 0xFF).toByte())
            }
        }
    }
    write.writeFully(body)
    write.flush()
}
