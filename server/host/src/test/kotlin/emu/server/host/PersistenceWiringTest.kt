package emu.server.host

import emu.persistence.account.AccountStore
import emu.persistence.character.CharacterStore
import emu.persistence.character.CharacterSaveSink
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerSessionSave
import emu.persistence.chat.ChatAuditStore
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatChannel
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.character.CharacterSaveWriter
import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresDatabase
import emu.persistence.postgres.database.PostgresMigrator
import java.time.Instant

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication

class PersistenceModuleTest {
    @Test
    fun `module wires persistence contracts to postgres adapters`() {
        val application = koinApplication { modules(persistenceModule(loadServerConfig(emptyMap()).database)) }
        val koin = application.koin

        assertNotNull(koin.get<PostgresConfig>())
        val database = koin.get<PostgresDatabase>()
        assertNotNull(koin.get<PostgresMigrator>())
        assertNotNull(koin.get<AccountStore>())
        assertNotNull(koin.get<CharacterStore>())
        assertNotNull(koin.get<CharacterSaveSink>())
        assertNotNull(koin.get<ChatAuditStore>())
        val characterSaves = koin.get<CharacterSaveWriter>()
        val chatAudits = koin.get<ChatAuditSink>()
        assertNotNull(koin.get<ChatAuditWriter>())
        application.close()

        assertTrue(database.isClosed)
        assertFalse(characterSaves.submit(PlayerSessionSave(1, PlayerPosition(3222, 3218, 0), 0)))
        assertFalse(chatAudits.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "late", Instant.EPOCH)))
    }
}
