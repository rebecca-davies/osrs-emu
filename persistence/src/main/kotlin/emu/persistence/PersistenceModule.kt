package emu.persistence

import org.koin.dsl.module

/** Persistence services shared by the gateway's connection coroutines. */
val persistenceModule =
    module {
        single { PostgresConfig.fromEnvironment() }
        single { PasswordHasher() }
        single { PostgresDatabase(get()) }
        single { PlayerRepository(get()) }
        single { AccountService(get(), get()) }
    }
