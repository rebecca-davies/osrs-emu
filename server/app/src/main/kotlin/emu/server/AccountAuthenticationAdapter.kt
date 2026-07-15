package emu.server

import emu.persistence.AccountAuthenticationResult
import emu.persistence.AccountService
import emu.persistence.PlayerRank
import emu.server.session.AccountPrivilege
import emu.server.session.AuthenticationDecision
import emu.server.session.AuthenticatedPrincipal

/** Adapts persisted account authentication into the login service's identity contract. */
internal fun AccountService.authenticateLogin(username: String, password: CharArray): AuthenticationDecision =
    when (val result = loginOrCreate(username, password)) {
        is AccountAuthenticationResult.Authenticated ->
            AuthenticationDecision.Authenticated(
                AuthenticatedPrincipal(
                    accountId = result.account.id,
                    username = result.account.username,
                    displayName = result.account.displayName,
                    privilege = result.account.rank.toPrivilege(),
                ),
            )
        AccountAuthenticationResult.InvalidCredentials -> AuthenticationDecision.Rejected
    }

private fun PlayerRank.toPrivilege(): AccountPrivilege =
    when (this) {
        PlayerRank.PLAYER -> AccountPrivilege.PLAYER
        PlayerRank.MODERATOR -> AccountPrivilege.MODERATOR
        PlayerRank.ADMINISTRATOR -> AccountPrivilege.ADMINISTRATOR
    }
