package emu.persistence.postgres

import com.zaxxer.hikari.HikariDataSource
import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresDatabase
import emu.persistence.postgres.database.PostgresPoolConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PostgresPoolTest {
    @Test
    fun `database exposes a bounded lazily started Hikari pool and closes it`() {
        val config =
            PostgresConfig(
                    jdbcUrl = "jdbc:postgresql://127.0.0.1:1/unreachable",
                    username = "test",
                    password = "test",
                )
        val poolConfig =
            PostgresPoolConfig(
                maximumSize = 3,
                minimumIdle = 0,
                connectionTimeout = 3.seconds,
                validationTimeout = 1.seconds,
                idleTimeout = 1.minutes,
                maxLifetime = 2.minutes,
            )
        val database = PostgresDatabase(config, poolConfig, "osrsemu-test-postgres")
        val pool = assertIs<HikariDataSource>(database.dataSource)

        assertEquals(3, pool.maximumPoolSize)
        assertEquals(0, pool.minimumIdle)
        assertEquals(3.seconds.inWholeMilliseconds, pool.connectionTimeout)
        assertEquals(1.seconds.inWholeMilliseconds, pool.validationTimeout)
        assertEquals(1.minutes.inWholeMilliseconds, pool.idleTimeout)
        assertEquals(2.minutes.inWholeMilliseconds, pool.maxLifetime)
        assertEquals(-1, pool.initializationFailTimeout)
        assertEquals("true", pool.dataSourceProperties.getProperty("tcpKeepAlive"))
        assertEquals("5", pool.dataSourceProperties.getProperty("connectTimeout"))
        assertEquals("10", pool.dataSourceProperties.getProperty("socketTimeout"))
        assertEquals(
            "-c statement_timeout=5000",
            pool.dataSourceProperties.getProperty("options"),
        )

        database.close()
        database.close()
        assertTrue(pool.isClosed)
    }
}
