package emu.protocol.osrs239

import emu.netcore.codec.CodecRepository
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.codec.MessageDecoder
import emu.netcore.codec.MessageEncoder
import org.koin.core.Koin

/**
 * Assembles an immutable [CodecRepository] by COLLECTING every [MessageDecoder]/[MessageEncoder]
 * bound into [this] Koin instance, rather than a per-domain `bindDecoder(...).bindEncoder(...)`
 * chain at a top-level site (CLAUDE.md §5a addendum). Each domain module (`js5Module`,
 * `loginModule`, `gameModule`) declares its own codecs exactly once; which domains end up in the
 * assembled repository is controlled entirely by which modules were loaded into [this] Koin
 * instance — e.g. the gateway builds one repository from `js5Module` alone for the JS5 pipeline and
 * a separate one from `gameModule` alone for the post-login game stage, keeping the two wire
 * protocols' opcode spaces isolated from each other exactly as the hand-written `installJs5`/
 * `installGame` chains did before.
 */
fun Koin.buildCodecRepository(): CodecRepository {
    val builder = CodecRepositoryBuilder()
    getAll<MessageDecoder<*>>().forEach { builder.bindDecoder(it) }
    getAll<MessageEncoder<*>>().forEach { builder.bindEncoder(it) }
    return builder.build()
}
