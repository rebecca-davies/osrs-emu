package emu.persistence

import java.sql.Timestamp

/** Thin append-only JDBC repository for auditable player chat. */
class ChatRepository(private val database: PostgresDatabase) {
    fun appendBatch(messages: List<ChatAuditMessage>) {
        if (messages.isEmpty()) return
        database.connection { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "INSERT INTO chat_messages(player_id, channel, message, created_at) VALUES (?, ?, ?, ?)",
                ).use { statement ->
                    for (message in messages) {
                        statement.setLong(1, message.playerId)
                        statement.setInt(2, message.channel.id)
                        statement.setString(3, message.message)
                        statement.setTimestamp(4, Timestamp.from(message.createdAt))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }
}
