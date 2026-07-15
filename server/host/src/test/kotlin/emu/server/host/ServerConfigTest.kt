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
                    "OSRS_GAME_CONNECTION_WORKER_THREADS" to "6",
                    "OSRS_GAME_ENTRY_WORKER_THREADS" to "2",
                    "OSRS_GAME_INPUT_QUEUE_CAPACITY" to "256",
                    "OSRS_GAME_INPUT_LIMIT_PER_CYCLE" to "12",
                    "OSRS_GAME_OUTPUT_QUEUE_CAPACITY" to "6",
                    "OSRS_GAME_ROUTE_SEARCH_LIMIT_PER_CYCLE" to "24",
                    "OSRS_GAME_COLLISION_LOAD_QUEUE_CAPACITY" to "64",
                    "OSRS_GAME_COLLISION_LOAD_WORKER_THREADS" to "3",
                    "OSRS_GAME_COLLISION_LOAD_SHUTDOWN_TIMEOUT_SECONDS" to "4",
                    "OSRS_WORLD_COMMAND_QUEUE_CAPACITY" to "512",
                    "OSRS_WORLD_COMMAND_LIMIT_PER_CYCLE" to "48",
                    "OSRS_COORDINATOR_WORLD_ENTRY_TIMEOUT_SECONDS" to "7",
                    "OSRS_DATABASE_URL" to "jdbc:postgresql://database/osrsemu",
                    "OSRS_DATABASE_USER" to "server-user",
                    "OSRS_DATABASE_PASSWORD" to "secret",
                    "OSRS_DATABASE_LOGIN_POOL_MAXIMUM_SIZE" to "4",
                    "OSRS_DATABASE_LOGIN_POOL_MINIMUM_IDLE" to "2",
                    "OSRS_DATABASE_LOGIN_POOL_CONNECTION_TIMEOUT_MS" to "3000",
                    "OSRS_DATABASE_LOGIN_POOL_VALIDATION_TIMEOUT_MS" to "1000",
                    "OSRS_DATABASE_LOGIN_POOL_IDLE_TIMEOUT_MS" to "60000",
                    "OSRS_DATABASE_LOGIN_POOL_MAX_LIFETIME_MS" to "120000",
                    "OSRS_DATABASE_WORLD_POOL_MAXIMUM_SIZE" to "7",
                    "OSRS_DATABASE_WORLD_POOL_MINIMUM_IDLE" to "3",
                    "OSRS_DATABASE_WORLD_POOL_CONNECTION_TIMEOUT_MS" to "4000",
                    "OSRS_DATABASE_WORLD_POOL_VALIDATION_TIMEOUT_MS" to "1500",
                    "OSRS_DATABASE_WORLD_POOL_IDLE_TIMEOUT_MS" to "120000",
                    "OSRS_DATABASE_WORLD_POOL_MAX_LIFETIME_MS" to "180000",
                    "OSRS_DATABASE_CONNECT_TIMEOUT_SECONDS" to "4",
                    "OSRS_DATABASE_SOCKET_TIMEOUT_SECONDS" to "9",
                    "OSRS_DATABASE_STATEMENT_TIMEOUT_MS" to "2500",
                ),
            )

        assertEquals("/srv/osrsemu/cache", config.assets.cacheDirectory.path)
        assertEquals("/run/secrets/rsa", config.assets.rsaPropertiesFile.path)
        assertEquals("127.0.0.2", config.gateway.bindHost)
        assertEquals(43595, config.gateway.port)
        assertEquals(8.seconds, config.gateway.classificationTimeout)
        assertEquals(12.seconds, config.login.authenticationTimeout)
        assertEquals(10, config.login.authentication.cost)
        assertEquals(10.seconds, config.js5.handshakeTimeout)
        assertEquals(20.seconds, config.js5.frameIdleTimeout)
        assertEquals(45.seconds, config.world.connection.idleTimeout)
        assertEquals(6, config.world.connectionWorkerThreads)
        assertEquals(2, config.world.entryWorkerThreads)
        assertEquals(256, config.world.connection.inputQueue.capacity)
        assertEquals(12, config.world.connection.inputQueue.maxPerCycle)
        assertEquals(6, config.world.connection.outputQueueCapacity)
        assertEquals(24, config.world.routes.maxPerCycle)
        assertEquals(64, config.world.collisionLoads.capacity)
        assertEquals(3, config.world.collisionLoads.workerThreads)
        assertEquals(4.seconds, config.world.collisionLoads.shutdownTimeout)
        assertEquals(512, config.world.commands.capacity)
        assertEquals(48, config.world.commands.maxPerCycle)
        assertEquals(7.seconds, config.coordinator.worldEntryTimeout)
        assertEquals("jdbc:postgresql://database/osrsemu", config.database.jdbcUrl)
        assertEquals("server-user", config.database.username)
        assertEquals(4, config.database.loginPool.maximumSize)
        assertEquals(2, config.database.loginPool.minimumIdle)
        assertEquals(3.seconds, config.database.loginPool.connectionTimeout)
        assertEquals(1.seconds, config.database.loginPool.validationTimeout)
        assertEquals(1.minutes, config.database.loginPool.idleTimeout)
        assertEquals(2.minutes, config.database.loginPool.maxLifetime)
        assertEquals(7, config.database.worldPool.maximumSize)
        assertEquals(3, config.database.worldPool.minimumIdle)
        assertEquals(4.seconds, config.database.worldPool.connectionTimeout)
        assertEquals(1500, config.database.worldPool.validationTimeout.inWholeMilliseconds)
        assertEquals(2.minutes, config.database.worldPool.idleTimeout)
        assertEquals(3.minutes, config.database.worldPool.maxLifetime)
        assertEquals(4, config.database.operations.connectTimeoutSeconds)
        assertEquals(9, config.database.operations.socketTimeoutSeconds)
        assertEquals(2500, config.database.operations.statementTimeoutMillis)
    }

    @Test
    fun `environment loader retains development defaults`() {
        val config = loadServerConfig(emptyMap())

        assertEquals("cache-data", config.assets.cacheDirectory.path)
        assertEquals("server-rsa.properties", config.assets.rsaPropertiesFile.path)
        assertEquals("0.0.0.0", config.gateway.bindHost)
        assertEquals(43594, config.gateway.port)
        assertEquals(15.seconds, config.gateway.classificationTimeout)
        assertEquals(15.seconds, config.login.authenticationTimeout)
        assertEquals(15.seconds, config.js5.handshakeTimeout)
        assertEquals(30.seconds, config.js5.frameIdleTimeout)
        assertEquals(30.seconds, config.world.connection.idleTimeout)
        assertEquals(128, config.world.connection.inputQueue.capacity)
        assertEquals(32, config.world.connection.inputQueue.maxPerCycle)
        assertEquals(15.seconds, config.coordinator.worldEntryTimeout)
        assertEquals(12, config.login.authentication.cost)
        assertEquals("jdbc:postgresql://127.0.0.1:54330/osrsemu", config.database.jdbcUrl)
        assertEquals(4, config.database.loginPool.maximumSize)
        assertEquals(10, config.database.worldPool.maximumSize)
    }

    @Test
    fun `environment loader rejects malformed database pool settings`() {
        assertFailsWith<IllegalArgumentException> {
            loadServerConfig(mapOf("OSRS_DATABASE_LOGIN_POOL_MAXIMUM_SIZE" to "many"))
        }
        assertFailsWith<IllegalArgumentException> {
            loadServerConfig(mapOf("OSRS_DATABASE_WORLD_POOL_MAXIMUM_SIZE" to "many"))
        }
    }

    @Test
    fun `database configuration never exposes its password`() {
        val config = loadServerConfig(mapOf("OSRS_DATABASE_PASSWORD" to "top-secret"))

        assertFalse("top-secret" in config.database.toString())
    }
}
