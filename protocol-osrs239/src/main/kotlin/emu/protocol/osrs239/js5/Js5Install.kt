package emu.protocol.osrs239.js5

import emu.netcore.codec.CodecRepositoryBuilder

/**
 * Registers every JS5 wire codec — the two group-request decoders (urgent/prefetch), every control
 * frame decoder ([Js5Prot.CONTROL_OPCODES]), and the group-response encoder — in one small,
 * greppable place (CLAUDE.md §5a design doc, section B). Codecs have no dependencies (unlike the
 * handlers in `emu.gateway.js5.installJs5Handlers`, which need the cache [emu.cache.store.Store]
 * and the connection's XOR cipher), so this lives alongside the codec/message types themselves
 * rather than in `gateway`.
 */
fun CodecRepositoryBuilder.installJs5(): CodecRepositoryBuilder = this
    .bindDecoder(Js5RequestDecoder(prefetch = false))
    .bindDecoder(Js5RequestDecoder(prefetch = true))
    .apply { Js5Prot.CONTROL_OPCODES.forEach { bindDecoder(Js5ControlDecoder(it)) } }
    .bindEncoder(Js5ResponseEncoder)
