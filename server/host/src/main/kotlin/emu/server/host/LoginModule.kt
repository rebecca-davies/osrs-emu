package emu.server.host

import emu.crypto.RsaKeyPair
import emu.server.login.LoginServer
import emu.server.login.LoginService
import emu.server.login.auth.AccountAuthenticator
import emu.server.login.auth.BcryptConfig
import emu.server.login.auth.BcryptPasswordHasher
import emu.server.login.auth.LoginAuthenticator
import emu.server.login.auth.PasswordHasher
import emu.server.login.config.LoginExecutionConfig
import org.koin.dsl.onClose
import org.koin.dsl.module

/** Defines the login service from host-owned key, account, and execution capabilities. */
internal fun loginModule(
    rsaKeyPair: RsaKeyPair?,
    loginConfig: LoginExecutionConfig,
    bcryptConfig: BcryptConfig,
) = module {
    single<PasswordHasher> { BcryptPasswordHasher(bcryptConfig) }
    single<LoginAuthenticator> { AccountAuthenticator(get(), get()) }
    single<LoginService> { LoginServer(rsaKeyPair, get(), loginConfig) } onClose { it?.close() }
}
