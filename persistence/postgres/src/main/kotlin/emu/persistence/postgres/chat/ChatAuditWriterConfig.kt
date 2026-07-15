package emu.persistence.postgres.chat

/** Bounded asynchronous chat-audit delivery settings. */
data class ChatAuditWriterConfig(
    val capacity: Int = 1_024,
    val batchSize: Int = 64,
    val flushMillis: Long = 100,
    val retryMillis: Long = 1_000,
    val closeTimeoutMillis: Long = 5_000,
) {
    init {
        require(capacity > 0 && batchSize > 0) { "chat audit queue and batch sizes must be positive" }
        require(flushMillis > 0 && retryMillis > 0 && closeTimeoutMillis > 0) {
            "chat audit timing must be positive"
        }
    }
}
