package emu.netcore.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.message.IncomingMessage
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot

interface MessageDecoder<T : IncomingMessage> {
    val prot: Prot
    fun decode(buf: JagexBuffer): T
}

interface MessageEncoder<T : OutgoingMessage> {
    val prot: Prot

    /**
     * Returns the exact bytes to write for this message (excluding any opcode prefix, which the
     * pipeline adds per-protocol). Cleaner than a caller-sized buffer for variable-size packets.
     */
    fun encode(cipher: StreamCipher, message: T): ByteArray
}
