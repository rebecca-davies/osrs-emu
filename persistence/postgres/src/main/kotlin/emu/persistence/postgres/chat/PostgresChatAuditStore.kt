package emu.persistence.postgres.chat

import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditStore
import emu.persistence.postgres.database.PostgresDatabase
import java.sql.Timestamp

private const val INSERT_CHAT_SQL =
    "INSERT INTO chat_messages(player_id, channel, message, created_at) VALUES (?, ?, ?, ?)"

/** PostgreSQL append-only chat audit adapter. */
class PostgresChatAuditStore(private val database: PostgresDatabase) : ChatAuditStore {
    override fun append(messages: List<ChatAuditMessage>) {
        if (messages.isEmpty()) return
        database.transaction { connection ->
            connection.prepareStatement(INSERT_CHAT_SQL).use { statement ->
                for (message in messages) {
                    statement.setLong(1, message.playerId)
                    statement.setInt(2, message.channel.id)
                    statement.setString(3, message.text)
                    statement.setTimestamp(4, Timestamp.from(message.createdAt))
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }
}
