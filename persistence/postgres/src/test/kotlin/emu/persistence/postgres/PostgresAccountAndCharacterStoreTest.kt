package emu.persistence.postgres

import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterBodyKits
import emu.game.player.appearance.CharacterColors
import emu.game.player.appearance.CharacterGender
import emu.persistence.account.AccountRank
import emu.persistence.character.model.CharacterChatFilters
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterSave
import emu.persistence.postgres.account.PostgresAccountRankStore
import emu.persistence.postgres.account.PostgresAccountStore
import emu.persistence.postgres.character.storage.PostgresCharacterStore
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private val SPAWN = CharacterPosition(3222, 3218, 0)
private val TEST_APPEARANCE =
    CharacterAppearance(
        CharacterGender.FEMALE,
        CharacterBodyKits(hair = 55, jaw = 306, torso = 60, arms = 66, hands = 68, legs = 78, feet = 80),
        CharacterColors(hair = 29, torso = 28, legs = 27, feet = 5, skin = 13),
    )

class PostgresAccountAndCharacterStoreTest {
    @Test
    fun `concurrent account creation initializes exactly one character`() {
        migratedTestDatabase().use { database ->
            val appearanceCreations = AtomicInteger()
            val accounts =
                PostgresAccountStore(database) {
                    appearanceCreations.incrementAndGet()
                    TEST_APPEARANCE
                }
            val ranks = PostgresAccountRankStore(database)
            val characters = PostgresCharacterStore(database)
            val name = "T_${UUID.randomUUID().toString().take(8)}"

            val username = name.lowercase().replace('_', ' ')
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val results =
                try {
                    val attempts =
                        List(2) { attempt ->
                            executor.submit(
                                Callable {
                                    ready.countDown()
                                    check(start.await(10, TimeUnit.SECONDS)) {
                                        "concurrent account creation did not start"
                                    }
                                    accounts.create(username, name, "bcrypt-hash-$attempt")
                                },
                            )
                        }
                    check(ready.await(10, TimeUnit.SECONDS)) { "concurrent account creators were not ready" }
                    start.countDown()
                    attempts.map { it.get(10, TimeUnit.SECONDS) }
                } finally {
                    executor.shutdownNow()
                }
            val created = results.filterNotNull().single()
            assertEquals(name, created.account.displayName)
            assertEquals(username, created.account.username)

            val character = requireNotNull(characters.load(created.account.id))
            assertEquals(SPAWN, character.position)
            assertEquals(0, character.playTimeSeconds)
            assertEquals(emptyMap(), character.varps)
            assertEquals(TEST_APPEARANCE, character.appearance)
            assertEquals(TEST_APPEARANCE, requireNotNull(characters.load(created.account.id)).appearance)
            assertNull(accounts.create(created.account.username, name, "different-hash"))
            assertEquals(1, appearanceCreations.get())

            ranks.setRank(created.account.id, AccountRank.ADMINISTRATOR)
            assertEquals(
                AccountRank.ADMINISTRATOR,
                requireNotNull(accounts.findByUsername(created.account.username)).account.rank,
            )
        }
    }

    @Test
    fun `connection failure does not generate a character appearance`() {
        val database = migratedTestDatabase()
        val appearanceCreations = AtomicInteger()
        val accounts =
            PostgresAccountStore(database) {
                appearanceCreations.incrementAndGet()
                TEST_APPEARANCE
            }
        database.close()

        assertFailsWith<SQLException> {
            accounts.create("offline-${UUID.randomUUID()}", "Offline", "bcrypt-hash")
        }
        assertEquals(0, appearanceCreations.get())
    }

    @Test
    fun `save point atomically updates position absolute playtime and sparse varps`() {
        migratedTestDatabase().use { database ->
            val accounts = PostgresAccountStore(database) { TEST_APPEARANCE }
            val characters = PostgresCharacterStore(database)
            val name = "V${UUID.randomUUID().toString().take(8)}"
            val account = requireNotNull(accounts.create(name.lowercase(), name, "bcrypt-hash")).account

            characters.save(
                CharacterSave(
                    account.id,
                    CharacterPosition(3200, 3201, 1),
                    playTimeSeconds = 42,
                    appearance = TEST_APPEARANCE,
                    dirtyVarps = mapOf(173 to 0, 1055 to Int.MAX_VALUE),
                    chatFilters = CharacterChatFilters(3, 1, 2),
                ),
            )
            characters.save(
                CharacterSave(
                    account.id,
                    CharacterPosition(3202, 3203, 2),
                    playTimeSeconds = 50,
                    appearance = TEST_APPEARANCE,
                    dirtyVarps = mapOf(173 to 1),
                    chatFilters = CharacterChatFilters(3, 1, 2),
                ),
            )

            val loaded = requireNotNull(characters.load(account.id))
            assertEquals(CharacterPosition(3202, 3203, 2), loaded.position)
            assertEquals(50, loaded.playTimeSeconds)
            assertEquals(mapOf(173 to 1, 1055 to Int.MAX_VALUE), loaded.varps)
            assertEquals(CharacterChatFilters(3, 1, 2), loaded.chatFilters)
            assertEquals(TEST_APPEARANCE, loaded.appearance)

            characters.save(
                CharacterSave(
                    account.id,
                    CharacterPosition(3200, 3201, 1),
                    playTimeSeconds = 42,
                    appearance = TEST_APPEARANCE,
                    dirtyVarps = mapOf(173 to 1),
                ),
            )
            assertEquals(50, requireNotNull(characters.load(account.id)).playTimeSeconds)

            characters.save(
                CharacterSave(
                    account.id,
                    SPAWN,
                    playTimeSeconds = 0,
                    appearance = TEST_APPEARANCE,
                    dirtyVarps = mapOf(173 to 0),
                ),
            )
            assertEquals(mapOf(1055 to Int.MAX_VALUE), requireNotNull(characters.load(account.id)).varps)
        }
    }
}
