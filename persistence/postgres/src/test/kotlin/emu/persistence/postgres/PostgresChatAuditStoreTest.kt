package emu.persistence.postgres

import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatChannel
import emu.persistence.postgres.account.PostgresAccountStore
import emu.persistence.postgres.chat.PostgresChatAuditStore
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresChatAuditStoreTest {
    @Test
    fun `append stores channel text and server timestamp`() {
        migratedTestDatabase().use { database ->
            val accounts = PostgresAccountStore(database)
            val name = "C${UUID.randomUUID().toString().take(8)}"
            val account = requireNotNull(accounts.create(name.lowercase(), name, "bcrypt-hash")).account
            val at = Instant.parse("2026-07-15T00:00:00Z")

            PostgresChatAuditStore(database).append(
                listOf(ChatAuditMessage(account.id, ChatChannel.PUBLIC, "hello", at)),
            )

            database.connection { connection ->
                connection.prepareStatement(
                    "SELECT channel, message, created_at FROM chat_messages " +
                        "WHERE player_id = ? ORDER BY id DESC LIMIT 1",
                ).use { statement ->
                    statement.setLong(1, account.id)
                    statement.executeQuery().use { result ->
                        result.next()
                        assertEquals(ChatChannel.PUBLIC.id, result.getInt("channel"))
                        assertEquals("hello", result.getString("message"))
                        assertEquals(at, result.getTimestamp("created_at").toInstant())
                    }
                }
            }
        }
    }
}
