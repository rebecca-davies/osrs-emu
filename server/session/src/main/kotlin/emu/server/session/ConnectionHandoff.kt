package emu.server.session

/** Connection state needed by the game service after login completes. */
data class ConnectionBootstrap(
    val isaac: IsaacBootstrap,
    val principal: AuthenticatedPrincipal,
)

/** Login-owned authentication context retained until the coordinator finishes admission. */
data class AuthenticatedSession(
    val connection: ConnectionBootstrap,
    val reconnect: Boolean,
)

/** Authenticated connection and accepted reservation transferred to the game service. */
data class ConnectionHandoff(
    val connection: ConnectionBootstrap,
    val reservation: ReservationDecision.Accepted,
)
