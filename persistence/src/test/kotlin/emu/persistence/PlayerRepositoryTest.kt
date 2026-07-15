package emu.persistence

import java.sql.SQLException
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayerRepositoryTest {
    private var database: PostgresDatabase? = null

    @AfterTest
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun `first login creates an account and later logins authenticate case insensitively`() {
        val fixture = fixtureOrNull() ?: return
        val name = "T_${UUID.randomUUID().toString().take(8)}"
        val password = "correct horse".toCharArray()

        val created = fixture.accounts.loginOrCreate(name, password)
        val account = assertIs<AccountAuthenticationResult.Authenticated>(created)
        assertEquals(true, account.created)
        assertEquals(name, account.account.displayName)
        assertEquals(name.lowercase().replace('_', ' '), account.account.username)
        assertEquals(PlayerRank.PLAYER, account.account.rank)
        val character = requireNotNull(fixture.players.loadCharacter(account.account.id))
        assertEquals(SPAWN, character.position)
        assertEquals(0, character.playTimeSeconds)

        val authenticated = fixture.accounts.loginOrCreate(name.lowercase().replace('_', ' '), password)
        assertEquals(
            account.account,
            assertIs<AccountAuthenticationResult.Authenticated>(authenticated).account,
        )
        assertEquals(
            AccountAuthenticationResult.InvalidCredentials,
            fixture.accounts.loginOrCreate(name, "wrong password".toCharArray()),
        )
    }

    @Test fun `rank changes are loaded on the next login`() {
        val fixture = fixtureOrNull() ?: return
        val name = "R${UUID.randomUUID().toString().take(8)}"
        val password = "rank password".toCharArray()
        val player = assertIs<AccountAuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password),
        ).account

        fixture.players.setRank(player.id, PlayerRank.ADMINISTRATOR)

        val loaded = assertIs<AccountAuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password),
        ).account
        assertEquals(PlayerRank.ADMINISTRATOR, loaded.rank)
    }

    @Test
    fun `logout save updates position and accumulates play time atomically`() {
        val fixture = fixtureOrNull() ?: return
        val name = "T${UUID.randomUUID().toString().take(8)}"
        val password = "session password".toCharArray()
        val account = assertIs<AccountAuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password),
        ).account

        fixture.players.saveSession(account.id, PlayerPosition(3200, 3201, 1), playedSeconds = 42)
        fixture.players.saveSession(account.id, PlayerPosition(3202, 3203, 2), playedSeconds = 8)

        val loaded = requireNotNull(fixture.players.loadCharacter(account.id))
        assertEquals(PlayerPosition(3202, 3203, 2), loaded.position)
        assertEquals(50, loaded.playTimeSeconds)
    }

    @Test
    fun `game service loads character state by authenticated account id`() {
        val fixture = fixtureOrNull() ?: return
        val name = "G${UUID.randomUUID().toString().take(8)}"
        val created = assertIs<AccountAuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, "game load".toCharArray()),
        ).account

        assertEquals(created.id, fixture.players.loadCharacter(created.id)?.id)
    }

    @Test
    fun `account varps are sparse and flushed only with the logout save point`() {
        val fixture = fixtureOrNull() ?: return
        val name = "V${UUID.randomUUID().toString().take(8)}"
        val password = "varp password".toCharArray()
        val account = assertIs<AccountAuthenticationResult.Authenticated>(
            fixture.accounts.loginOrCreate(name, password),
        ).account
        val player = requireNotNull(fixture.players.loadCharacter(account.id))

        assertEquals(emptyMap(), player.varps)
        fixture.players.saveSession(
            player.id,
            SPAWN,
            playedSeconds = 0,
            dirtyVarps = mapOf(173 to 0, 1055 to Int.MAX_VALUE),
        )

        val loaded = requireNotNull(fixture.players.loadCharacter(player.id))
        assertEquals(mapOf(1055 to Int.MAX_VALUE), loaded.varps)

        fixture.players.saveSession(player.id, SPAWN, playedSeconds = 0, dirtyVarps = mapOf(173 to 1))
        val updated = requireNotNull(fixture.players.loadCharacter(player.id))
        assertEquals(mapOf(173 to 1, 1055 to Int.MAX_VALUE), updated.varps)

        fixture.players.saveSession(player.id, SPAWN, playedSeconds = 0, dirtyVarps = mapOf(173 to 0))
        val cleared = requireNotNull(fixture.players.loadCharacter(player.id))
        assertEquals(mapOf(1055 to Int.MAX_VALUE), cleared.varps)
    }

    private fun fixtureOrNull(): Fixture? {
        val database = PostgresDatabase(PostgresConfig.fromEnvironment(System.getenv()))
        this.database = database
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
