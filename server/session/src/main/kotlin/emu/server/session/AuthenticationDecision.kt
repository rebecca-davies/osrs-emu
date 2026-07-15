package emu.server.session

/** Result of validating account credentials. */
sealed interface AuthenticationDecision {
    data class Authenticated(val account: AuthenticatedAccount) : AuthenticationDecision

    data object Rejected : AuthenticationDecision
}
