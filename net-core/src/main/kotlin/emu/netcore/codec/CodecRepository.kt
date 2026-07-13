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
        require(decoders.put(decoder.prot.opcode, decoder) == null) {
            "duplicate decoder for opcode ${decoder.prot.opcode}"
        }
        return this
    }

    fun bindEncoder(encoder: MessageEncoder<*>): CodecRepositoryBuilder {
        // Resolve the concrete OutgoingMessage type parameter from the encoder's generic supertype.
        val type = resolveOutgoingType(encoder)
        require(encoders.put(type, encoder) == null) { "duplicate encoder for $type" }
        return this
    }

    fun build(): CodecRepository = CodecRepository(decoders.toMap(), encoders.toMap())

    @Suppress("UNCHECKED_CAST")
    private fun resolveOutgoingType(encoder: MessageEncoder<*>): Class<out OutgoingMessage> {
        for (t in encoder.javaClass.genericInterfaces) {
            if (t is java.lang.reflect.ParameterizedType &&
                t.rawType == MessageEncoder::class.java
            ) {
                return t.actualTypeArguments[0] as Class<out OutgoingMessage>
            }
        }
        error("cannot resolve OutgoingMessage type for ${encoder.javaClass}")
    }
}
