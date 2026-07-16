package emu.server.session.authentication

import emu.server.session.account.AuthenticatedAccount

/** Result of validating account credentials. */
sealed interface AuthenticationDecision {
    data class Authenticated(val account: AuthenticatedAccount) : AuthenticationDecision

    data object Rejected : AuthenticationDecision
}

/** Authenticated account and cipher state retained until the coordinator finishes world entry. */
data class AuthenticatedSession(
    val account: AuthenticatedAccount,
    val isaac: IsaacBootstrap,
)

/** Result of preparing the authenticated account to enter a world. */
sealed interface AuthenticationCompletion {
    data class Accepted(val playerIndex: Int) : AuthenticationCompletion {
        init {
            require(playerIndex > 0) { "player index must be positive" }
        }
    }

    data class Rejected(val reason: AuthenticationRejection) : AuthenticationCompletion
}

/** Client-visible reason an authenticated session cannot enter the world. */
enum class AuthenticationRejection {
    ALREADY_ONLINE,
    WORLD_FULL,
    WORLD_UNAVAILABLE,
}
