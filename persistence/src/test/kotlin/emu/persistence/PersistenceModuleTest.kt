package emu.persistence

import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.dsl.koinApplication

class PersistenceModuleTest {
    @Test
    fun `module wires the database repository and account service`() {
        val koin = koinApplication { modules(persistenceModule) }.koin

        assertNotNull(koin.get<PostgresConfig>())
        assertNotNull(koin.get<PostgresDatabase>())
        assertNotNull(koin.get<PasswordHasher>())
        assertNotNull(koin.get<PlayerRepository>())
        assertNotNull(koin.get<AccountService>())
        assertNotNull(koin.get<ChatRepository>())
        koin.get<ChatAuditWriter>().close()
    }
}
