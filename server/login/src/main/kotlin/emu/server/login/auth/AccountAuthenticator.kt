package emu.server.login.auth

import emu.persistence.account.AccountRecord
import emu.persistence.account.AccountStore
import emu.persistence.account.PlayerRank
import emu.persistence.account.StoredAccount
import emu.server.session.AccountId
import emu.server.session.AccountPrivilege
import emu.server.session.AuthenticatedAccount
import emu.server.session.AuthenticationDecision

/** Bcrypt authentication and atomic first-login account creation policy. */
class AccountAuthenticator(
    private val accounts: AccountStore,
    private val passwords: PasswordHasher,
) : LoginAuthenticator {
    override fun authenticate(username: String, password: CharArray): AuthenticationDecision {
        val identity = PlayerIdentity.parse(username) ?: return AuthenticationDecision.Rejected
        val existing = accounts.findByUsername(identity.username)
        if (existing != null) return authenticate(existing, password)

        val hash = passwords.hash(password)
        val created = accounts.create(identity.username, identity.displayName, hash)
        if (created != null) return created.account.authenticatedDecision()

        val concurrent = accounts.findByUsername(identity.username)
            ?: error("account creation conflicted but the account row was not visible")
        return authenticate(concurrent, password)
    }

    private fun authenticate(stored: StoredAccount, password: CharArray): AuthenticationDecision =
        if (passwords.verify(password, stored.passwordHash)) {
            stored.account.authenticatedDecision()
        } else {
            AuthenticationDecision.Rejected
        }
}

private fun AccountRecord.authenticatedDecision(): AuthenticationDecision =
    AuthenticationDecision.Authenticated(
        AuthenticatedAccount(
            accountId = AccountId(id),
            privilege = rank.toPrivilege(),
        ),
    )

private fun PlayerRank.toPrivilege(): AccountPrivilege =
    when (this) {
        PlayerRank.PLAYER -> AccountPrivilege.PLAYER
        PlayerRank.MODERATOR -> AccountPrivilege.MODERATOR
        PlayerRank.ADMINISTRATOR -> AccountPrivilege.ADMINISTRATOR
    }
