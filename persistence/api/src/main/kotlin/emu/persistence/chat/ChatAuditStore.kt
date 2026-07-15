package emu.persistence.chat

/** Append-only durable storage for accepted chat audit entries. */
fun interface ChatAuditStore {
    fun append(messages: List<ChatAuditMessage>)
}
