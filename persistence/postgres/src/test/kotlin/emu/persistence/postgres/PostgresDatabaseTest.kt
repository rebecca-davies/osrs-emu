package emu.persistence.postgres

import emu.persistence.postgres.database.PostgresMigrator
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PostgresDatabaseTest {
    @Test
    fun `migration is idempotent and creates the complete character schema`() {
        migratedTestDatabase().use { database ->
            PostgresMigrator(database).migrate()

            database.connection { connection ->
                connection.prepareStatement(
                    "SELECT count(*) FROM schema_migrations WHERE version BETWEEN 1 AND 7",
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(7, result.getInt(1))
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
                connection.prepareStatement(
                    "SELECT count(*) FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = 'players' " +
                        "AND column_name IN (" +
                        "'gender', 'hair_kit', 'jaw_kit', 'torso_kit', 'arms_kit', 'hands_kit', " +
                        "'legs_kit', 'feet_kit', 'hair_color', 'torso_color', 'legs_color', " +
                        "'feet_color', 'skin_color') " +
                        "AND is_nullable = 'NO' AND column_default IS NOT NULL",
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(13, result.getInt(1))
                    }
                }
            }
        }
    }

    @Test
    fun `identity-kit columns reject values in the worn-item range`() {
        migratedTestDatabase().use { database ->
            database.connection { connection ->
                val columns =
                    listOf(
                        "hair_kit",
                        "jaw_kit",
                        "torso_kit",
                        "arms_kit",
                        "hands_kit",
                        "legs_kit",
                        "feet_kit",
                    )
                val maximumKitSql =
                    "INSERT INTO players(username, password_hash, display_name, ${columns.joinToString()}) " +
                        "VALUES (?, ?, ?, ${columns.joinToString { "?" }})"
                connection.prepareStatement(maximumKitSql).use { statement ->
                    statement.setString(1, "kit-max-${UUID.randomUUID()}")
                    statement.setString(2, "bcrypt-hash")
                    statement.setString(3, "Kit max")
                    columns.indices.forEach { index -> statement.setInt(index + 4, 1_791) }
                    assertEquals(1, statement.executeUpdate())
                }
                columns.forEach { column ->
                    val username = "kit-overlap-${UUID.randomUUID()}"
                    assertFailsWith<SQLException> {
                        connection.prepareStatement(
                            "INSERT INTO players(username, password_hash, display_name, $column) VALUES (?, ?, ?, ?)",
                        ).use { statement ->
                            statement.setString(1, username)
                            statement.setString(2, "bcrypt-hash")
                            statement.setString(3, "Kit overlap")
                            statement.setInt(4, 1_792)
                            statement.executeUpdate()
                        }
                    }
                }
            }
        }
    }
}
