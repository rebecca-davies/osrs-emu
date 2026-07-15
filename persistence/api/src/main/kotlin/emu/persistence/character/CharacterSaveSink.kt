package emu.persistence.character

/** Non-waiting bounded admission boundary for immutable character save points. */
fun interface CharacterSaveSink {
    /** Returns true after retaining an isolated snapshot; false leaves ownership with the caller. */
    fun submit(save: PlayerSessionSave): Boolean
}
