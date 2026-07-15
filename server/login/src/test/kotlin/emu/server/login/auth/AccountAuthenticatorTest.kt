package emu.server.login.auth

import emu.persistence.account.AccountRecord
import emu.persistence.account.AccountStore
import emu.persistence.account.PlayerRank
import emu.persistence.account.StoredAccount
import emu.server.session.AuthenticationDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AccountAuthenticatorTest {
    @Test
    fun `first login creates an account and later login verifies its password`() {
        val store = InMemoryAccountStore()
        val authenticator = AccountAuthenticator(store, BcryptPasswordHasher(BcryptConfig(cost = 4)))

        val created = assertIs<AuthenticationDecision.Authenticated>(
            authenticator.authenticate("Rebecca_Bird", "correct horse".toCharArray()),
        )
        assertEquals("rebecca bird", created.principal.username)
        assertEquals("Rebecca_Bird", created.principal.displayName)

        assertIs<AuthenticationDecision.Authenticated>(
            authenticator.authenticate("REBECCA BIRD", "correct horse".toCharArray()),
        )
        assertEquals(
            AuthenticationDecision.Rejected,
            authenticator.authenticate("Rebecca_Bird", "wrong password".toCharArray()),
        )
    }

    private class InMemoryAccountStore : AccountStore {
        private val accounts = mutableMapOf<String, StoredAccount>()

        override fun findByUsername(username: String): StoredAccount? = accounts[username]

        override fun create(username: String, displayName: String, passwordHash: String): StoredAccount? {
            if (username in accounts) return null
            return StoredAccount(
                AccountRecord(1, username, displayName, PlayerRank.PLAYER),
                passwordHash,
            ).also { accounts[username] = it }
        }
    }
}
