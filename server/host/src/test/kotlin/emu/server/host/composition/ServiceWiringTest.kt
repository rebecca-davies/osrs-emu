package emu.server.host.composition

import emu.cache.store.Store
import emu.crypto.RsaKeyPair
import emu.persistence.account.AccountStore
import emu.persistence.account.StoredAccount
import emu.protocol.osrs239.js5.buildJs5CodecRepository
import emu.server.js5.Js5Service
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.login.LoginService
import emu.server.login.auth.BcryptConfig
import emu.server.login.config.LoginExecutionConfig
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class ServiceWiringTest {
    @Test
    fun `host constructs one js5 service from its owned capabilities`() {
        val application =
            koinApplication {
                modules(
                    js5Module(
                        store = EmptyStore,
                        codecs = buildJs5CodecRepository(),
                        config = Js5ExecutionConfig(workerThreads = 1),
                    ),
                )
            }

        val service = application.koin.get<Js5Service>()

        assertSame(service, application.koin.get<Js5Service>())
        application.close()
    }

    @Test
    fun `host constructs one login service with host supplied account storage`() {
        val accounts = module { single<AccountStore> { EmptyAccounts } }
        val application =
            koinApplication {
                modules(
                    accounts,
                    loginModule(
                        rsaKeyPair = TEST_RSA_KEY,
                        loginConfig =
                            LoginExecutionConfig(
                                workerThreads = 1,
                                authentication = BcryptConfig(cost = 4),
                            ),
                    ),
                )
            }

        val service = application.koin.get<LoginService>()

        assertSame(service, application.koin.get<LoginService>())
        application.close()
    }

    private object EmptyStore : Store {
        override fun read(archive: Int, group: Int): ByteArray? = null
    }

    private object EmptyAccounts : AccountStore {
        override fun findByUsername(username: String): StoredAccount? = null

        override fun create(username: String, displayName: String, passwordHash: String): StoredAccount? = null
    }

    private companion object {
        val TEST_RSA_KEY =
            RsaKeyPair(
                modulus = BigInteger.valueOf(3_233),
                publicExp = BigInteger.valueOf(17),
                privateExp = BigInteger.valueOf(2_753),
            )
    }
}
