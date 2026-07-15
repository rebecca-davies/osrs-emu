package emu.netcore.codec

import emu.netcore.message.OutgoingMessage

class CodecRepository internal constructor(
    private val decodersByOpcode: Map<Int, MessageDecoder<*>>,
    private val encodersByType: Map<Class<out OutgoingMessage>, MessageEncoder<*>>,
) {
    fun decoder(opcode: Int): MessageDecoder<*>? = decodersByOpcode[opcode]
    fun encoder(type: Class<out OutgoingMessage>): MessageEncoder<*>? = encodersByType[type]
}

class CodecRepositoryBuilder {
    private val decoders = HashMap<Int, MessageDecoder<*>>()
    private val encoders = HashMap<Class<out OutgoingMessage>, MessageEncoder<*>>()

    fun bindDecoder(decoder: MessageDecoder<*>): CodecRepositoryBuilder {
        require(decoders.putIfAbsent(decoder.prot.opcode, decoder) == null) {
            "duplicate decoder for opcode ${decoder.prot.opcode}"
        }
        return this
    }

    fun bindEncoder(encoder: MessageEncoder<*>): CodecRepositoryBuilder {
        require(encoders.putIfAbsent(encoder.messageType, encoder) == null) {
            "duplicate encoder for ${encoder.messageType}"
        }
        return this
    }

    fun build(): CodecRepository = CodecRepository(decoders.toMap(), encoders.toMap())
}
