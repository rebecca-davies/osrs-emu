package emu.persistence.postgres.character

/** Capacity, retry policy, and terminal deadline for asynchronous character saves. */
data class CharacterSaveWriterConfig(
    val capacity: Int = 2_048,
    val pollMillis: Long = 100,
    val retryMillis: Long = 1_000,
    val maxAttempts: Int = 5,
    val closeTimeoutMillis: Long = 5_000,
) {
    init {
        require(capacity > 0) { "character save queue capacity must be positive" }
        require(pollMillis > 0 && retryMillis > 0 && closeTimeoutMillis > 0) {
            "character save timing must be positive"
        }
        require(maxAttempts > 0) { "character save attempts must be positive" }
    }
}
