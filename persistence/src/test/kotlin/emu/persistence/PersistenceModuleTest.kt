package emu.persistence

import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication

class PersistenceModuleTest {
    @Test
    fun `module wires the database repository and account service`() {
        val application = koinApplication { modules(persistenceModule) }
        val koin = application.koin

        assertNotNull(koin.get<PostgresConfig>())
        val database = koin.get<PostgresDatabase>()
        assertNotNull(koin.get<PasswordHasher>())
        assertNotNull(koin.get<PlayerRepository>())
        assertNotNull(koin.get<AccountService>())
        assertNotNull(koin.get<ChatRepository>())
        koin.get<ChatAuditWriter>().close()
        application.close()

        assertTrue((database.dataSource as HikariDataSource).isClosed)
    }
}
