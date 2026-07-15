package emu.persistence.character

/** Observable state of one asynchronous character save point. */
enum class CharacterWriteState {
    PENDING,
    DURABLE,
    FAILED,
}
