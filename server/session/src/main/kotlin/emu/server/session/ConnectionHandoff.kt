package emu.server.session

/** Exact login-owned values transferred to the game service after world entry is accepted. */
data class ConnectionHandoff(
    val accountId: AccountId,
    val isaac: IsaacBootstrap,
    val reservation: ReservationDecision.Accepted,
)
