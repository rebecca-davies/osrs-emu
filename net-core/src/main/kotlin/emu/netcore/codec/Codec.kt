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

    /** Concrete message type used as the encoder-registry key. */
    val messageType: Class<T>

    /** Encodes the message body without the pipeline-owned opcode prefix. */
    fun encode(cipher: StreamCipher, message: T): ByteArray
}
