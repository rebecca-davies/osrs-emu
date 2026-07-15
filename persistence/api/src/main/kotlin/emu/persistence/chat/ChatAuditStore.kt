package emu.persistence.chat

/** Append-only durable storage for admitted chat audit entries. */
fun interface ChatAuditStore {
    fun append(messages: List<ChatAuditMessage>)
}
