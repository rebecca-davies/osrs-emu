package emu.persistence

import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresDatabaseTest {
    @Test
    fun `migration is idempotent and creates the complete character schema`() {
        val database = PostgresDatabase(PostgresConfig.fromEnvironment())
        if (!database.isReachable()) {
            println("SKIP: PostgreSQL is unreachable; run docker compose up -d postgres")
            return
        }

        database.migrate()
        database.migrate()

        database.connection { connection ->
            connection.prepareStatement(
                "SELECT count(*) FROM schema_migrations WHERE version IN (1, 2, 3)",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(3, result.getInt(1))
                }
            }
            val tables = listOf("players", "player_skills", "player_items", "player_varps")
            for (table in tables) {
                connection.prepareStatement(
                    "SELECT count(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name = ?",
                ).use { statement ->
                    statement.setString(1, table)
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(1, result.getInt(1), "missing table $table")
                    }
                }
            }
            connection.prepareStatement(
                "SELECT count(*) FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'players' " +
                    "AND column_name IN ('x', 'y', 'plane', 'play_time_seconds', 'rank') AND is_nullable = 'NO'",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(5, result.getInt(1))
                }
            }
        }
    }
}

private fun PostgresDatabase.isReachable(): Boolean =
    try {
        connection { it.isValid(2) }
    } catch (_: SQLException) {
        false
    }
