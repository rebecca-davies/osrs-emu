package emu.protocol.osrs239.js5

import emu.netcore.codec.MessageDecoder
import emu.netcore.codec.MessageEncoder
import emu.protocol.osrs239.js5.codec.Js5ControlDecoder
import emu.protocol.osrs239.js5.codec.Js5RequestDecoder
import emu.protocol.osrs239.js5.codec.Js5ResponseEncoder
import emu.protocol.osrs239.js5.prot.Js5Prot
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Declares every JS5 wire codec as a Koin singleton bound to [MessageDecoder]/[MessageEncoder] so
 * [emu.protocol.osrs239.buildCodecRepository] can COLLECT them (`getOfType/getAll<...>()`) instead
 * of a growing `bindDecoder(...).bindDecoder(...)` chain at a top-level site (CLAUDE.md §5a
 * addendum). Each `single` is qualified by name — Koin identifies a definition by primary type +
 * qualifier, and several of these bind the SAME concrete class ([Js5ControlDecoder]) with different
 * constructor arguments, which would otherwise collide.
 */
val js5Module = module {
    single(named("js5.groupRequest")) { Js5RequestDecoder(prefetch = false) } bind MessageDecoder::class
    single(named("js5.groupRequestPrefetch")) { Js5RequestDecoder(prefetch = true) } bind MessageDecoder::class
    Js5Prot.CONTROL_OPCODES.forEach { opcode ->
        single(named("js5.control.$opcode")) { Js5ControlDecoder(opcode) } bind MessageDecoder::class
    }
    single(named("js5.groupResponse")) { Js5ResponseEncoder } bind MessageEncoder::class
}
