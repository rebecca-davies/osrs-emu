package emu.transport.pipeline

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully

/**
 * Encodes one packet body, optionally writes its cipher-adjusted smart opcode and plaintext
 * variable-length prefix, then flushes when [flush] is true.
 *
 * Body encoding precedes opcode encoding because body codecs may consume [cipher]. Each opcode
 * smart byte consumes one cipher value. [Prot.VAR_BYTE] uses a u8 length and [Prot.VAR_SHORT] a
 * big-endian u16 length; fixed packets and opcode-less protocols write no length prefix.
 */
suspend fun <T : OutgoingMessage> writePacket(
    write: ByteWriteChannel,
    encoder: MessageEncoder<T>,
    message: T,
    cipher: StreamCipher,
    writeOpcode: Boolean,
    flush: Boolean = true,
) {
    val body = encoder.encode(cipher, message)
    if (writeOpcode) {
        when (encoder.prot.size) {
            Prot.VAR_BYTE -> require(body.size <= UByte.MAX_VALUE.toInt()) {
                "VAR_BYTE packet body is too large: ${body.size}"
            }
            Prot.VAR_SHORT -> require(body.size <= UShort.MAX_VALUE.toInt()) {
                "VAR_SHORT packet body is too large: ${body.size}"
            }
        }
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
    if (flush) write.flush()
}
