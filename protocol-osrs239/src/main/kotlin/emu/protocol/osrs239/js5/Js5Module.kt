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

/** JS5 codecs, qualified where multiple definitions share a concrete type. */
val js5Module = module {
    single(named("js5.groupRequest")) { Js5RequestDecoder(prefetch = false) } bind MessageDecoder::class
    single(named("js5.groupRequestPrefetch")) { Js5RequestDecoder(prefetch = true) } bind MessageDecoder::class
    Js5Prot.CONTROL_OPCODES.forEach { opcode ->
        single(named("js5.control.$opcode")) { Js5ControlDecoder(opcode) } bind MessageDecoder::class
    }
    single(named("js5.groupResponse")) { Js5ResponseEncoder } bind MessageEncoder::class
}
