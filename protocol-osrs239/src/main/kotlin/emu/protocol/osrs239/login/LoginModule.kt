package emu.protocol.osrs239.login

import emu.netcore.codec.MessageEncoder
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.codec.ServerSessionKeyEncoder
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Positional pre-ISAAC login response encoders. */
val loginModule = module {
    single(named("login.serverSessionKey")) { ServerSessionKeyEncoder } bind MessageEncoder::class
    single(named("login.response")) { LoginResponseEncoder } bind MessageEncoder::class
}
