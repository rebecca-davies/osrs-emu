package emu.persistence.postgres.character

import emu.persistence.character.CharacterStore
import emu.persistence.character.PlayerRecord
import emu.persistence.character.PlayerSessionSave
import emu.persistence.postgres.database.PostgresDatabase

/** PostgreSQL character aggregate adapter. */
class PostgresCharacterStore(database: PostgresDatabase) : CharacterStore {
    private val reader = PostgresCharacterReader(database)
    private val writer = PostgresSessionWriter(database)

    override fun load(playerId: Long): PlayerRecord? = reader.load(playerId)

    override fun save(save: PlayerSessionSave) = writer.save(save)
}
