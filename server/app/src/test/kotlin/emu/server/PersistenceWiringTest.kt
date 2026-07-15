package emu.server

import emu.persistence.AccountService
import emu.persistence.ChatAuditWriter
import emu.persistence.ChatRepository
import emu.persistence.PasswordHasher
import emu.persistence.PlayerRepository
import emu.persistence.PostgresConfig
import emu.persistence.PostgresDatabase

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication

class PersistenceModuleTest {
    @Test
    fun `module wires the database repository and account service`() {
        val application = koinApplication { modules(persistenceModule(PostgresConfig.fromEnvironment(System.getenv()))) }
        val koin = application.koin

        assertNotNull(koin.get<PostgresConfig>())
        val database = koin.get<PostgresDatabase>()
        assertNotNull(koin.get<PasswordHasher>())
        assertNotNull(koin.get<PlayerRepository>())
        assertNotNull(koin.get<AccountService>())
        assertNotNull(koin.get<ChatRepository>())
        koin.get<ChatAuditWriter>().close()
        application.close()

        assertTrue(database.isClosed)
    }
}
