package emu.server.host

import emu.persistence.account.AccountStore
import emu.persistence.character.CharacterStore
import emu.persistence.chat.ChatAuditStore
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresDatabase
import emu.persistence.postgres.database.PostgresMigrator

import kotlin.test.Test
import kotlin.test.assertNotNull
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
        assertNotNull(koin.get<ChatAuditStore>())
        koin.get<ChatAuditWriter>().close()
        application.close()

        assertTrue(database.isClosed)
    }
}
