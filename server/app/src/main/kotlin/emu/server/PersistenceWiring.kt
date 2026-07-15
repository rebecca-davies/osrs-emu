package emu.server

import emu.persistence.AccountService
import emu.persistence.ChatAuditSink
import emu.persistence.ChatAuditWriter
import emu.persistence.ChatRepository
import emu.persistence.PasswordHasher
import emu.persistence.PlayerRepository
import emu.persistence.PostgresConfig
import emu.persistence.PostgresDatabase

import org.koin.dsl.onClose
import org.koin.dsl.module

/** Persistence services constructed from server-owned database configuration. */
fun persistenceModule(config: PostgresConfig) =
    module {
        single { config }
        single { PasswordHasher() }
        single { PostgresDatabase(get()) } onClose { it?.close() }
        single { PlayerRepository(get()) }
        single { ChatRepository(get()) }
        single { ChatAuditWriter(get<ChatRepository>()::appendBatch) }
        single<ChatAuditSink> { get<ChatAuditWriter>() }
        single { AccountService(get(), get()) }
    }
