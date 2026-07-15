package emu.protocol.osrs239

import emu.netcore.codec.CodecRepository
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.codec.MessageDecoder
import emu.netcore.codec.MessageEncoder
import org.koin.core.Koin

/** Collects codecs from the modules loaded in this Koin instance into an immutable registry. */
fun Koin.buildCodecRepository(): CodecRepository {
    val builder = CodecRepositoryBuilder()
    getAll<MessageDecoder<*>>().forEach { builder.bindDecoder(it) }
    getAll<MessageEncoder<*>>().forEach { builder.bindEncoder(it) }
    return builder.build()
}
