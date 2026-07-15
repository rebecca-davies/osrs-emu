package emu.persistence.postgres

import emu.persistence.postgres.database.PostgresMigrator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresDatabaseTest {
    @Test
    fun `migration is idempotent and creates the complete character schema`() {
        migratedTestDatabase().use { database ->
            PostgresMigrator(database).migrate()

            database.connection { connection ->
                connection.prepareStatement(
                    "SELECT count(*) FROM schema_migrations WHERE version IN (1, 2, 3, 4, 5)",
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(5, result.getInt(1))
                    }
                }
                val tables = listOf("players", "player_skills", "player_items", "player_varps", "chat_messages")
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
            }
        }
    }
}
