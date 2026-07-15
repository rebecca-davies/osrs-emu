package emu.server.host

import emu.persistence.account.AccountStore
import emu.persistence.character.CharacterStore
import emu.persistence.character.CharacterWriteQueue
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

/** Constructs persistence services from server-owned database configuration. */
fun persistenceModule(
    config: PostgresConfig,
    characterSaveConfig: CharacterSaveWriterConfig = CharacterSaveWriterConfig(),
) =
    module {
        single { config }
        single(DatabaseQualifier.world) {
            PostgresDatabase(get(), config.worldPool, "osrsemu-world-postgres")
        }
        single(DatabaseQualifier.login) {
            PostgresDatabase(get(), config.loginPool, "osrsemu-login-postgres")
        }
        single { PostgresMigrator(get(DatabaseQualifier.world)) }
        single<AccountStore> { PostgresAccountStore(get(DatabaseQualifier.login)) }
        single<CharacterStore> { PostgresCharacterStore(get(DatabaseQualifier.world)) }
        single { CharacterSaveWriter(get(), characterSaveConfig) }
        single<CharacterWriteQueue> { get<CharacterSaveWriter>() }
        single<ChatAuditStore> { PostgresChatAuditStore(get(DatabaseQualifier.world)) }
        single { ChatAuditWriter(get()) }
        single<ChatAuditSink> { get<ChatAuditWriter>() }
        single(createdAtStart = true) {
            PersistenceLifecycle(
                characterSaves = get(),
                chatAudits = get(),
                worldDatabase = get(DatabaseQualifier.world),
                accountDatabase = get(DatabaseQualifier.login),
            )
        } onClose { it?.close() }
    }
