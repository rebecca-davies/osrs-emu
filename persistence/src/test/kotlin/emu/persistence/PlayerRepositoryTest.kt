package emu.persistence

import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayerRepositoryTest {
    @Test
    fun `first login creates an account and later logins authenticate case insensitively`() {
        val fixture = fixtureOrNull() ?: return
        val name = "T_${UUID.randomUUID().toString().take(8)}"
        val password = "correct horse".toCharArray()

        val created = fixture.accounts.loginOrCreate(name, password, SPAWN)
        val player = assertIs<AuthenticationResult.Authenticated>(created)
        assertEquals(true, player.created)
        assertEquals(name, player.player.displayName)
        assertEquals(name.lowercase().replace('_', ' '), player.player.username)
        assertEquals(SPAWN, player.player.position)
        assertEquals(0, player.player.playTimeSeconds)
        assertEquals(PlayerRank.PLAYER, player.player.rank)

        val authenticated = fixture.accounts.loginOrCreate(name.lowercase().replace('_', ' '), password, SPAWN)
        assertEquals(
            player.player,
            assertIs<AuthenticationResult.Authenticated>(authenticated).player,
        )
        assertEquals(
            AuthenticationResult.InvalidCredentials,
            fixture.accounts.loginOrCreate(name, "wrong password".toCharArray(), SPAWN),
        )
    }

    @Test fun `rank changes are loaded on the next login`() {
        val fixture = fixtureOrNull() ?: return
        val name = "R${UUID.randomUUID().toString().take(8)}"
        val password = "rank password".toCharArray()
        val player = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player

        fixture.players.setRank(player.id, PlayerRank.ADMINISTRATOR)

        val loaded = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player
        assertEquals(PlayerRank.ADMINISTRATOR, loaded.rank)
    }

    @Test
    fun `logout save updates position and accumulates play time atomically`() {
        val fixture = fixtureOrNull() ?: return
        val name = "T${UUID.randomUUID().toString().take(8)}"
        val password = "session password".toCharArray()
        val player = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player

        fixture.players.saveSession(player.id, PlayerPosition(3200, 3201, 1), playedSeconds = 42)
        fixture.players.saveSession(player.id, PlayerPosition(3202, 3203, 2), playedSeconds = 8)

        val loaded = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player
        assertEquals(PlayerPosition(3202, 3203, 2), loaded.position)
        assertEquals(50, loaded.playTimeSeconds)
    }

    @Test
    fun `account varps are sparse and flushed only with the logout save point`() {
        val fixture = fixtureOrNull() ?: return
        val name = "V${UUID.randomUUID().toString().take(8)}"
        val password = "varp password".toCharArray()
        val player = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player

        assertEquals(emptyMap(), player.varps)
        fixture.players.saveSession(
            player.id,
            SPAWN,
            playedSeconds = 0,
            dirtyVarps = mapOf(173 to 0, 1055 to Int.MAX_VALUE),
        )

        val loaded = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player
        assertEquals(mapOf(1055 to Int.MAX_VALUE), loaded.varps)

        fixture.players.saveSession(player.id, SPAWN, playedSeconds = 0, dirtyVarps = mapOf(173 to 1))
        val updated = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player
        assertEquals(mapOf(173 to 1, 1055 to Int.MAX_VALUE), updated.varps)

        fixture.players.saveSession(player.id, SPAWN, playedSeconds = 0, dirtyVarps = mapOf(173 to 0))
        val cleared = assertIs<AuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password, SPAWN),
        ).player
        assertEquals(mapOf(1055 to Int.MAX_VALUE), cleared.varps)
    }

    private fun fixtureOrNull(): Fixture? {
        val database = PostgresDatabase(PostgresConfig.fromEnvironment())
        try {
            if (!database.connection { it.isValid(2) }) return null
        } catch (_: SQLException) {
            println("SKIP: PostgreSQL is unreachable; run docker compose up -d postgres")
            return null
        }
        database.migrate()
        val players = PlayerRepository(database)
        return Fixture(players, AccountService(players, PasswordHasher(cost = 4)))
    }

    private data class Fixture(val players: PlayerRepository, val accounts: AccountService)

    private companion object {
        val SPAWN = PlayerPosition(3222, 3218, 0)
    }
}
