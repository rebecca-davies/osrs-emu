package emu.persistence.character

/** Non-blocking boundary from the world thread to durable character write-back. */
fun interface CharacterWriteQueue {
    fun submit(save: PlayerSessionSave): CharacterWriteCompletion?
}
