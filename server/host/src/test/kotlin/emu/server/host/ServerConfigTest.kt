package emu.server.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ServerConfigTest {
    @Test
    fun `environment loader creates one typed process configuration`() {
        val config =
            loadServerConfig(
                mapOf(
                    "OSRS_CACHE_DIR" to "/srv/osrsemu/cache",
                    "OSRS_SERVER_RSA_PROPERTIES" to "/run/secrets/rsa",
                    "OSRS_GATEWAY_BIND_HOST" to "127.0.0.2",
                    "OSRS_GATEWAY_PORT" to "43595",
                    "OSRS_GATEWAY_CLASSIFICATION_TIMEOUT_SECONDS" to "8",
                    "OSRS_LOGIN_AUTHENTICATION_TIMEOUT_SECONDS" to "12",
                    "OSRS_LOGIN_BCRYPT_COST" to "10",
                    "OSRS_JS5_HANDSHAKE_TIMEOUT_SECONDS" to "10",
                    "OSRS_JS5_IDLE_TIMEOUT_SECONDS" to "20",
                    "OSRS_GAME_IDLE_TIMEOUT_SECONDS" to "45",
                    "OSRS_COORDINATOR_ADMISSION_TIMEOUT_SECONDS" to "7",
                    "OSRS_DATABASE_URL" to "jdbc:postgresql://database/osrsemu",
                    "OSRS_DATABASE_USER" to "server-user",
                    "OSRS_DATABASE_PASSWORD" to "secret",
                    "OSRS_DATABASE_POOL_MAXIMUM_SIZE" to "4",
                    "OSRS_DATABASE_POOL_MINIMUM_IDLE" to "2",
                    "OSRS_DATABASE_POOL_CONNECTION_TIMEOUT_MS" to "3000",
                    "OSRS_DATABASE_POOL_VALIDATION_TIMEOUT_MS" to "1000",
                    "OSRS_DATABASE_POOL_IDLE_TIMEOUT_MS" to "60000",
                    "OSRS_DATABASE_POOL_MAX_LIFETIME_MS" to "120000",
                ),
            )

        assertEquals("/srv/osrsemu/cache", config.cacheDirectory.path)
        assertEquals("/run/secrets/rsa", config.rsaPropertiesFile.path)
        assertEquals("127.0.0.2", config.gateway.bindHost)
        assertEquals(43595, config.gateway.port)
        assertEquals(8.seconds, config.gateway.classificationTimeout)
        assertEquals(12.seconds, config.login.authenticationTimeout)
        assertEquals(10, config.authentication.cost)
        assertEquals(10.seconds, config.js5.handshakeTimeout)
        assertEquals(20.seconds, config.js5.frameIdleTimeout)
        assertEquals(45.seconds, config.world.idleTimeout)
        assertEquals(7.seconds, config.coordinator.admissionTimeout)
        assertEquals("jdbc:postgresql://database/osrsemu", config.database.jdbcUrl)
        assertEquals("server-user", config.database.username)
        assertEquals(4, config.database.pool.maximumSize)
        assertEquals(2, config.database.pool.minimumIdle)
        assertEquals(3.seconds, config.database.pool.connectionTimeout)
        assertEquals(1.seconds, config.database.pool.validationTimeout)
        assertEquals(1.minutes, config.database.pool.idleTimeout)
        assertEquals(2.minutes, config.database.pool.maxLifetime)
    }

    @Test
    fun `environment loader retains development defaults`() {
        val config = loadServerConfig(emptyMap())

        assertEquals("cache-data", config.cacheDirectory.path)
        assertEquals("server-rsa.properties", config.rsaPropertiesFile.path)
        assertEquals("0.0.0.0", config.gateway.bindHost)
        assertEquals(43594, config.gateway.port)
        assertEquals(15.seconds, config.gateway.classificationTimeout)
        assertEquals(15.seconds, config.login.authenticationTimeout)
        assertEquals(15.seconds, config.js5.handshakeTimeout)
        assertEquals(30.seconds, config.js5.frameIdleTimeout)
        assertEquals(30.seconds, config.world.idleTimeout)
        assertEquals(15.seconds, config.coordinator.admissionTimeout)
        assertEquals(12, config.authentication.cost)
        assertEquals("jdbc:postgresql://127.0.0.1:54330/osrsemu", config.database.jdbcUrl)
    }

    @Test
    fun `environment loader rejects malformed database pool settings`() {
        assertFailsWith<IllegalArgumentException> {
            loadServerConfig(mapOf("OSRS_DATABASE_POOL_MAXIMUM_SIZE" to "many"))
        }
    }

    @Test
    fun `database configuration never exposes its password`() {
        val config = loadServerConfig(mapOf("OSRS_DATABASE_PASSWORD" to "top-secret"))

        assertFalse("top-secret" in config.database.toString())
    }
}
