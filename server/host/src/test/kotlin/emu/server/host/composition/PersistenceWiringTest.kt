package emu.server.host.composition

import emu.persistence.account.AccountStore
import emu.persistence.character.CharacterStore
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterSave
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatAuditStore
import emu.persistence.chat.ChatChannel
import emu.persistence.postgres.character.writeback.CharacterSaveWriter
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresDatabase
import emu.persistence.postgres.database.PostgresMigrator
import emu.server.host.config.loadServerConfig
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication

class PersistenceModuleTest {
    @Test
    fun `module wires persistence contracts to postgres adapters`() {
        val application = koinApplication { modules(persistenceModule(loadServerConfig(emptyMap()).database)) }
        val koin = application.koin

        assertNotNull(koin.get<PostgresConfig>())
        val world = koin.get<PostgresDatabase>(DatabaseQualifier.world)
        val login = koin.get<PostgresDatabase>(DatabaseQualifier.login)
        val databases = listOf(world, login)
        assertEquals("osrsemu-world-postgres", world.poolName)
        assertEquals(10, world.maximumPoolSize)
        assertEquals("osrsemu-login-postgres", login.poolName)
        assertEquals(4, login.maximumPoolSize)
        assertFalse(world === login)
        assertNotNull(koin.get<PostgresMigrator>())
        assertNotNull(koin.get<AccountStore>())
        assertNotNull(koin.get<CharacterStore>())
        assertNotNull(koin.get<CharacterWriteQueue>())
        assertNotNull(koin.get<ChatAuditStore>())
        val characterSaves = koin.get<CharacterSaveWriter>()
        val chatAudits = koin.get<ChatAuditSink>()
        assertNotNull(koin.get<ChatAuditWriter>())
        application.close()

        assertTrue(databases.all(PostgresDatabase::isClosed))
        assertNull(characterSaves.submit(CharacterSave(1, CharacterPosition(3222, 3218, 0), 0)))
        assertFalse(chatAudits.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "late", Instant.EPOCH)))
    }
}
