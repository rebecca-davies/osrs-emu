package emu.persistence

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class PostgresConfigTest {
    @Test
    fun `development defaults match the local compose service`() {
        assertEquals(
            PostgresConfig(
                jdbcUrl = "jdbc:postgresql://127.0.0.1:54330/osrsemu",
                username = "osrsemu",
                password = "osrsemu-dev",
                pool =
                    PostgresPoolConfig(
                        maximumSize = 10,
                        minimumIdle = 1,
                        connectionTimeout = 5.seconds,
                        validationTimeout = 2.seconds,
                        idleTimeout = 10.minutes,
                        maxLifetime = 30.minutes,
                    ),
            ),
            PostgresConfig.fromEnvironment(emptyMap()),
        )
    }

    @Test
    fun `environment overrides every database setting`() {
        assertEquals(
            PostgresConfig("jdbc:postgresql://db/game", "gateway", "secret"),
            PostgresConfig.fromEnvironment(
                mapOf(
                    "OSRS_DATABASE_URL" to "jdbc:postgresql://db/game",
                    "OSRS_DATABASE_USER" to "gateway",
                    "OSRS_DATABASE_PASSWORD" to "secret",
                ),
            ),
        )
    }

    @Test
    fun `environment overrides typed pool settings`() {
        val config =
            PostgresConfig.fromEnvironment(
                mapOf(
                    "OSRS_DATABASE_POOL_MAXIMUM_SIZE" to "4",
                    "OSRS_DATABASE_POOL_MINIMUM_IDLE" to "2",
                    "OSRS_DATABASE_POOL_CONNECTION_TIMEOUT_MS" to "3000",
                    "OSRS_DATABASE_POOL_VALIDATION_TIMEOUT_MS" to "1000",
                    "OSRS_DATABASE_POOL_IDLE_TIMEOUT_MS" to "60000",
                    "OSRS_DATABASE_POOL_MAX_LIFETIME_MS" to "120000",
                ),
            )

        assertEquals(
            PostgresPoolConfig(
                maximumSize = 4,
                minimumIdle = 2,
                connectionTimeout = 3.seconds,
                validationTimeout = 1.seconds,
                idleTimeout = 1.minutes,
                maxLifetime = 2.minutes,
            ),
            config.pool,
        )
    }

    @Test
    fun `pool configuration rejects unsafe sizes and timeouts`() {
        assertFailsWith<IllegalArgumentException> {
            PostgresPoolConfig(maximumSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PostgresPoolConfig(connectionTimeout = 1.seconds, validationTimeout = 1.seconds)
        }
        assertFailsWith<IllegalArgumentException> {
            PostgresConfig.fromEnvironment(mapOf("OSRS_DATABASE_POOL_MAXIMUM_SIZE" to "many"))
        }
    }

    @Test fun `configuration string never exposes the database password`() {
        val rendered = PostgresConfig("jdbc:postgresql://db/game", "gateway", "top-secret").toString()

        assertFalse("top-secret" in rendered)
    }
}
