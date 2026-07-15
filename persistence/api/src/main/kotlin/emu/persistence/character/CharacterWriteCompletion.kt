package emu.persistence.character

/** Pollable confirmation that one character save point reached durable storage. */
fun interface CharacterWriteCompletion {
    fun state(): CharacterWriteState

    fun isDurable(): Boolean = state() == CharacterWriteState.DURABLE
}
