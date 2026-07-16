package emu.persistence.character.write

import emu.persistence.character.model.CharacterSave

/** Non-blocking boundary from the world thread to durable character write-back. */
fun interface CharacterWriteQueue {
    fun submit(save: CharacterSave): CharacterWriteCompletion?
}

/** Pollable confirmation that one character save point reached durable storage. */
fun interface CharacterWriteCompletion {
    fun state(): CharacterWriteState

    fun isDurable(): Boolean = state() == CharacterWriteState.DURABLE
}

/** Observable state of one asynchronous character save point. */
enum class CharacterWriteState {
    PENDING,
    DURABLE,
    FAILED,
}

/** Completed character write that is already durable. */
object DurableCharacterWrite : CharacterWriteCompletion {
    override fun state(): CharacterWriteState = CharacterWriteState.DURABLE
}
