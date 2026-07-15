package emu.persistence.character

/** Character load and write-behind save-point persistence. */
interface CharacterStore {
    fun load(playerId: Long): PlayerRecord?

    fun save(save: PlayerSessionSave)
}
