package emu.persistence.postgres.character.storage

import emu.persistence.character.CharacterStore
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.model.CharacterSave
import emu.persistence.postgres.database.PostgresDatabase

/** PostgreSQL character aggregate adapter. */
class PostgresCharacterStore(database: PostgresDatabase) : CharacterStore {
    private val reader = PostgresCharacterReader(database)
    private val writer = PostgresCharacterWriter(database)

    override fun load(characterId: Long): CharacterRecord? = reader.load(characterId)

    override fun save(save: CharacterSave) = writer.save(save)
}
