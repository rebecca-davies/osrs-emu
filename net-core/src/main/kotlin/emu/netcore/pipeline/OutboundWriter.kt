package emu.netcore.pipeline

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully

/**
 * Writes one outbound packet to [write]: encode the body, optionally prefix an opcode smart, flush.
 * The single reusable outbound-write path for every protocol stage — extracted out of
 * [ProtocolStage] so it can be unit-tested (and reused) independently of a running pipeline.
 *
 * Keystream ordering (read this before changing either side): [message] is encoded first via
 * [MessageEncoder.encode], passed the real [cipher] — this preserves encoders that consume the
 * cipher themselves to obfuscate their own body bytes (e.g. `Js5ResponseEncoder`'s per-byte XOR).
 * Only *after* that call, if [writeOpcode] is set, is the opcode written as a one-or-two-byte smart.
 * Each smart byte draws its own `cipher.nextInt()` and adds it modulo 256. Opcodes below 128 use one
 * byte; larger opcodes use `[128 + (opcode ushr 8), opcode and 0xFF]`. This mirrors the client's
 * `gSmart1or2Isaac`: it subtracts one keystream value from each byte, then combines the two decoded
 * bytes when the first is at least 128. The payload itself is never touched by this opcode step.
 * Net effect per protocol:
 *  - JS5 (`writeOpcode = false`): no opcode smart, so no extra `nextInt()` call is ever made here;
 *    the body's own XOR (via the real cipher passed into `encode`) is exactly as before.
 *  - Game packets (`writeOpcode = true`, real ISAAC cipher, bodies not cipher-consuming): body is
 *    written in the clear, and each opcode-smart byte is ISAAC-scrambled independently.
 *  - Everything under [emu.crypto.NopStreamCipher] (`nextInt() == 0`): opcode arithmetic is a
 *    no-op, so the wire contains the plaintext one-or-two-byte opcode smart.
 *
 * Length framing (rev-239): only when [writeOpcode] is set does a variable-size prot emit a
 * **plaintext** length between the (ISAAC-adjusted) opcode smart and the body — one u8 for
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
        val opcode = encoder.prot.opcode
        require(opcode in 0..0x7FFF) { "opcode must fit pSmart1or2: $opcode" }
        if (opcode < 128) {
            write.writeByte(((opcode + cipher.nextInt()) and 0xFF).toByte())
        } else {
            write.writeByte(((128 + (opcode ushr 8) + cipher.nextInt()) and 0xFF).toByte())
            write.writeByte((((opcode and 0xFF) + cipher.nextInt()) and 0xFF).toByte())
        }
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
