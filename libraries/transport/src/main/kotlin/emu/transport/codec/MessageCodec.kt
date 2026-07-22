package emu.transport.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.message.IncomingMessage
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot

/** Decodes one framed inbound protocol message. */
interface MessageDecoder<T : IncomingMessage> {
    val prot: Prot
    fun decode(buf: JagexBuffer): T
}

/** Encodes one outbound protocol message body. */
interface MessageEncoder<T : OutgoingMessage> {
    val prot: Prot

    /** Concrete message type used as the encoder-registry key. */
    val messageType: Class<T>

    /** Encodes the message body without the pipeline-owned opcode prefix. */
    fun encode(cipher: StreamCipher, message: T): ByteArray
}

/** Encodes a message body whose bytes never depend on or advance the connection cipher. */
interface CipherIndependentMessageEncoder<T : OutgoingMessage> : MessageEncoder<T> {
    fun encode(message: T): ByteArray

    override fun encode(cipher: StreamCipher, message: T): ByteArray = encode(message)
}
