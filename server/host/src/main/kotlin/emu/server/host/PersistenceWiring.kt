package emu.server.host

import emu.persistence.account.AccountStore
import emu.persistence.character.CharacterStore
import emu.persistence.character.CharacterSaveSink
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatAuditStore
import emu.persistence.postgres.account.PostgresAccountStore
import emu.persistence.postgres.character.PostgresCharacterStore
import emu.persistence.postgres.character.CharacterSaveWriter
import emu.persistence.postgres.character.CharacterSaveWriterConfig
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.chat.PostgresChatAuditStore
import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresDatabase
import emu.persistence.postgres.database.PostgresMigrator

import org.koin.dsl.onClose
import org.koin.dsl.module

/** Persistence services constructed from server-owned database configuration. */
fun persistenceModule(
    config: PostgresConfig,
    characterSaveConfig: CharacterSaveWriterConfig = CharacterSaveWriterConfig(),
) =
    module {
        single { config }
        single { PostgresDatabase(get()) }
        single { PostgresMigrator(get()) }
        single<AccountStore> { PostgresAccountStore(get()) }
        single<CharacterStore> { PostgresCharacterStore(get()) }
        single { CharacterSaveWriter(get(), characterSaveConfig) }
        single<CharacterSaveSink> { get<CharacterSaveWriter>() }
        single<ChatAuditStore> { PostgresChatAuditStore(get()) }
        single { ChatAuditWriter(get()) }
        single<ChatAuditSink> { get<ChatAuditWriter>() }
        single(createdAtStart = true) {
            PersistenceLifecycle(
                characterSaves = get(),
                chatAudits = get(),
                database = get(),
            )
        } onClose { it?.close() }
    }
