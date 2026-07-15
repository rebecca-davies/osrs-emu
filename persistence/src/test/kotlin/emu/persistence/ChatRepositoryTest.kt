package emu.persistence

import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChatRepositoryTest {
    @Test fun `appends auditable messages with player channel text and server time`() {
        val database = PostgresDatabase(PostgresConfig.fromEnvironment())
        try {
            try {
                if (!database.connection { it.isValid(2) }) return
            } catch (_: SQLException) {
                return
            }
            database.migrate()
            val players = PlayerRepository(database)
            val player = assertIs<AuthenticationResult.Authenticated>(
                AccountService(players, PasswordHasher(cost = 4)).loginOrCreate(
                    "C${UUID.randomUUID().toString().take(8)}",
                    "password".toCharArray(),
                    PlayerPosition(3222, 3218, 0),
                ),
            ).player
            val at = Instant.parse("2026-07-15T00:00:00Z")

            ChatRepository(database).appendBatch(listOf(ChatAuditMessage(player.id, ChatChannel.PUBLIC, "hello", at)))

            database.connection { connection ->
                connection.prepareStatement(
                    "SELECT channel, message, created_at FROM chat_messages " +
                        "WHERE player_id = ? ORDER BY id DESC LIMIT 1",
                ).use { statement ->
                    statement.setLong(1, player.id)
                    statement.executeQuery().use { result ->
                        result.next()
                        assertEquals(ChatChannel.PUBLIC.id, result.getInt("channel"))
                        assertEquals("hello", result.getString("message"))
                        assertEquals(at, result.getTimestamp("created_at").toInstant())
                    }
                }
            }
        } finally {
            database.close()
        }
    }
}
