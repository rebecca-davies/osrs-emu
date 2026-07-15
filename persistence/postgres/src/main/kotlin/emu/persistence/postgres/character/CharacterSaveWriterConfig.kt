package emu.persistence.postgres.character

/** Memory limits and shutdown warning threshold for asynchronous character saves. */
data class CharacterSaveWriterConfig(
    val capacity: Int = 2_048,
    val pollMillis: Long = 100,
    val retryMillis: Long = 1_000,
    val closeWarningMillis: Long = 5_000,
) {
    init {
        require(capacity > 0) { "character save queue capacity must be positive" }
        require(pollMillis > 0 && retryMillis > 0 && closeWarningMillis > 0) {
            "character save timing must be positive"
        }
    }
}
