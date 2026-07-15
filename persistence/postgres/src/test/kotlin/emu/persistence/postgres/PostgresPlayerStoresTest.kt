package emu.persistence.postgres

import emu.persistence.account.PlayerRank
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerSessionSave
import emu.persistence.character.PlayerChatFiltersRecord
import emu.persistence.postgres.account.PostgresAccountStore
import emu.persistence.postgres.account.PostgresAccountRankStore
import emu.persistence.postgres.character.PostgresCharacterStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private val SPAWN = PlayerPosition(3222, 3218, 0)

class PostgresPlayerStoresTest {
    @Test
    fun `account and character storage remain separate`() {
        migratedTestDatabase().use { database ->
            val accounts = PostgresAccountStore(database)
            val ranks = PostgresAccountRankStore(database)
            val characters = PostgresCharacterStore(database)
            val name = "T_${UUID.randomUUID().toString().take(8)}"

            val created = requireNotNull(accounts.create(name.lowercase().replace('_', ' '), name, "bcrypt-hash"))
            assertEquals(name, created.account.displayName)
            assertEquals(name.lowercase().replace('_', ' '), created.account.username)

            val character = requireNotNull(characters.load(created.account.id))
            assertEquals(SPAWN, character.position)
            assertEquals(0, character.playTimeSeconds)
            assertEquals(emptyMap(), character.varps)

            ranks.setRank(created.account.id, PlayerRank.ADMINISTRATOR)
            assertEquals(
                PlayerRank.ADMINISTRATOR,
                requireNotNull(accounts.findByUsername(created.account.username)).account.rank,
            )
        }
    }

    @Test
    fun `save point atomically updates position absolute playtime and sparse varps`() {
        migratedTestDatabase().use { database ->
            val accounts = PostgresAccountStore(database)
            val characters = PostgresCharacterStore(database)
            val name = "V${UUID.randomUUID().toString().take(8)}"
            val account = requireNotNull(accounts.create(name.lowercase(), name, "bcrypt-hash")).account

            characters.save(
                PlayerSessionSave(
                    account.id,
                    PlayerPosition(3200, 3201, 1),
                    playTimeSeconds = 42,
                    dirtyVarps = mapOf(173 to 0, 1055 to Int.MAX_VALUE),
                    chatFilters = PlayerChatFiltersRecord(3, 1, 2),
                ),
            )
            characters.save(
                PlayerSessionSave(
                    account.id,
                    PlayerPosition(3202, 3203, 2),
                    playTimeSeconds = 50,
                    dirtyVarps = mapOf(173 to 1),
                    chatFilters = PlayerChatFiltersRecord(3, 1, 2),
                ),
            )

            val loaded = requireNotNull(characters.load(account.id))
            assertEquals(PlayerPosition(3202, 3203, 2), loaded.position)
            assertEquals(50, loaded.playTimeSeconds)
            assertEquals(mapOf(173 to 1, 1055 to Int.MAX_VALUE), loaded.varps)
            assertEquals(PlayerChatFiltersRecord(3, 1, 2), loaded.chatFilters)

            characters.save(
                PlayerSessionSave(
                    account.id,
                    PlayerPosition(3200, 3201, 1),
                    playTimeSeconds = 42,
                    dirtyVarps = mapOf(173 to 1),
                ),
            )
            assertEquals(50, requireNotNull(characters.load(account.id)).playTimeSeconds)

            characters.save(PlayerSessionSave(account.id, SPAWN, 0, mapOf(173 to 0)))
            assertEquals(mapOf(1055 to Int.MAX_VALUE), requireNotNull(characters.load(account.id)).varps)
        }
    }
}
