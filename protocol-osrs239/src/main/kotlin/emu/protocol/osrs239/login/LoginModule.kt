package emu.protocol.osrs239.login

import emu.netcore.codec.MessageEncoder
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.codec.ServerSessionKeyEncoder
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Declares the login domain's two positional (pre-ISAAC) reply encoders as Koin singletons bound
 * to [MessageEncoder], collected the same way as [emu.protocol.osrs239.js5.js5Module] (CLAUDE.md
 * §5a addendum). Login has no [emu.netcore.codec.MessageDecoder]s — opcodes 14/16/18 are hand-rolled
 * positional reads (see `emu.gateway.login`), not opcode-dispatched via `CodecRepository`.
 */
val loginModule = module {
    single(named("login.serverSessionKey")) { ServerSessionKeyEncoder } bind MessageEncoder::class
    single(named("login.response")) { LoginResponseEncoder } bind MessageEncoder::class
}
