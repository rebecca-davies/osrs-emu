package emu.persistence.chat

import java.time.Instant

private const val MAX_CHAT_MESSAGE_LENGTH = 100

/** Immutable append-only chat audit entry. */
data class ChatAuditMessage(
    val playerId: Long,
    val channel: ChatChannel,
    val text: String,
    val createdAt: Instant,
) {
    init {
        require(playerId > 0) { "chat audit player id must be positive" }
        require(text.isNotBlank() && text.length <= MAX_CHAT_MESSAGE_LENGTH) {
            "invalid audited chat length"
        }
    }
}
