package emu.protocol.osrs239.login

import emu.netcore.codec.CodecRepository
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.codec.MessageEncoder
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.codec.ServerSessionKeyEncoder

/** Rev-239 login codecs. */
object LoginCodecs {
    val encoders: List<MessageEncoder<*>> = listOf(
        ServerSessionKeyEncoder,
        LoginResponseEncoder,
    )
}

/** Builds an immutable repository containing only rev-239 login codecs. */
fun buildLoginCodecRepository(): CodecRepository = CodecRepositoryBuilder()
    .also { builder -> LoginCodecs.encoders.forEach(builder::bindEncoder) }
    .build()
