package emu.persistence.chat

/** Non-blocking audit queue boundary used by the world cycle. */
fun interface ChatAuditSink {
    fun submit(message: ChatAuditMessage): Boolean
}
