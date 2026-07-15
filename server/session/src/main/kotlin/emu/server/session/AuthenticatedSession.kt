package emu.server.session

/** Authenticated account and cipher state retained until the coordinator finishes world entry. */
data class AuthenticatedSession(
    val account: AuthenticatedAccount,
    val isaac: IsaacBootstrap,
)
