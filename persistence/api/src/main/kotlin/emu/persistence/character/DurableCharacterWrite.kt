package emu.persistence.character

/** Completed character write that is already durable. */
object DurableCharacterWrite : CharacterWriteCompletion {
    override fun state(): CharacterWriteState = CharacterWriteState.DURABLE
}
