package emu.persistence.character

import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.model.CharacterSave

/** Character load and write-behind save-point persistence. */
interface CharacterStore {
    fun load(characterId: Long): CharacterRecord?

    fun save(save: CharacterSave)
}
