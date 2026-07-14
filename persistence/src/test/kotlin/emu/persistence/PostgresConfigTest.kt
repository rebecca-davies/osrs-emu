package emu.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PostgresConfigTest {
    @Test
    fun `development defaults match the local compose service`() {
        assertEquals(
            PostgresConfig(
                jdbcUrl = "jdbc:postgresql://127.0.0.1:54330/osrsemu",
                username = "osrsemu",
                password = "osrsemu-dev",
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

    @Test fun `configuration string never exposes the database password`() {
        val rendered = PostgresConfig("jdbc:postgresql://db/game", "gateway", "top-secret").toString()

        assertFalse("top-secret" in rendered)
    }
}
