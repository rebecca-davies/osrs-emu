package emu.persistence

import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PostgresPoolTest {
    @Test
    fun `database exposes a bounded lazily started Hikari pool and closes it`() {
        val database =
            PostgresDatabase(
                PostgresConfig(
                    jdbcUrl = "jdbc:postgresql://127.0.0.1:1/unreachable",
                    username = "test",
                    password = "test",
                    pool =
                        PostgresPoolConfig(
                            maximumSize = 3,
                            minimumIdle = 0,
                            connectionTimeout = 3.seconds,
                            validationTimeout = 1.seconds,
                            idleTimeout = 1.minutes,
                            maxLifetime = 2.minutes,
                        ),
                ),
            )
        val pool = assertIs<HikariDataSource>(database.dataSource)

        assertEquals(3, pool.maximumPoolSize)
        assertEquals(0, pool.minimumIdle)
        assertEquals(3.seconds.inWholeMilliseconds, pool.connectionTimeout)
        assertEquals(1.seconds.inWholeMilliseconds, pool.validationTimeout)
        assertEquals(1.minutes.inWholeMilliseconds, pool.idleTimeout)
        assertEquals(2.minutes.inWholeMilliseconds, pool.maxLifetime)
        assertEquals(-1, pool.initializationFailTimeout)
        assertEquals("true", pool.dataSourceProperties.getProperty("tcpKeepAlive"))

        database.close()
        database.close()

        assertTrue(pool.isClosed)
    }
}
