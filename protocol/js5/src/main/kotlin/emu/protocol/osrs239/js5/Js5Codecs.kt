package emu.protocol.osrs239.js5

import emu.transport.codec.CodecRepository
import emu.transport.codec.CodecRepositoryBuilder
import emu.transport.codec.MessageDecoder
import emu.transport.codec.MessageEncoder
import emu.protocol.osrs239.js5.codec.Js5ControlDecoder
import emu.protocol.osrs239.js5.codec.Js5RequestDecoder
import emu.protocol.osrs239.js5.codec.Js5ResponseEncoder
import emu.protocol.osrs239.js5.prot.Js5Prot

/** Rev-239 JS5 codecs. */
object Js5Codecs {
    val decoders: List<MessageDecoder<*>> = listOf(
        Js5RequestDecoder(prefetch = false),
        Js5RequestDecoder(prefetch = true),
    ) + Js5Prot.CONTROL_OPCODES.map(::Js5ControlDecoder)

    val encoders: List<MessageEncoder<*>> = listOf(Js5ResponseEncoder)
}

/** Builds an immutable repository containing only rev-239 JS5 codecs. */
fun buildJs5CodecRepository(): CodecRepository = CodecRepositoryBuilder()
    .also { builder ->
        Js5Codecs.decoders.forEach(builder::bindDecoder)
        Js5Codecs.encoders.forEach(builder::bindEncoder)
    }
    .build()
