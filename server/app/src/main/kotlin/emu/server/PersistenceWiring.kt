package emu.server

import emu.persistence.account.AccountStore
import emu.persistence.character.CharacterStore
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatAuditStore
import emu.persistence.postgres.account.PostgresAccountStore
import emu.persistence.postgres.character.PostgresCharacterStore
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.chat.PostgresChatAuditStore
import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresDatabase
import emu.persistence.postgres.database.PostgresMigrator

import org.koin.dsl.onClose
import org.koin.dsl.module

/** Persistence services constructed from server-owned database configuration. */
fun persistenceModule(config: PostgresConfig) =
    module {
        single { config }
        single { PostgresDatabase(get()) } onClose { it?.close() }
        single { PostgresMigrator(get()) }
        single<AccountStore> { PostgresAccountStore(get()) }
        single<CharacterStore> { PostgresCharacterStore(get()) }
        single<ChatAuditStore> { PostgresChatAuditStore(get()) }
        single { ChatAuditWriter(get()) }
        single<ChatAuditSink> { get<ChatAuditWriter>() }
    }
