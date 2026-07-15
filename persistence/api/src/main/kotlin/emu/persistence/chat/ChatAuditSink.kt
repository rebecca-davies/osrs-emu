package emu.persistence.chat

/** Non-blocking audit admission boundary used by the world cycle. */
fun interface ChatAuditSink {
    fun submit(message: ChatAuditMessage): Boolean
}
