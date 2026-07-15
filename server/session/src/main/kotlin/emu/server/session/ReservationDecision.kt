package emu.server.session

/** World-owned reservation decision returned before login success is written. */
sealed interface ReservationDecision {
    data class Accepted(
        val token: GameSessionToken,
        val playerIndex: Int,
    ) : ReservationDecision {
        init {
            require(playerIndex > 0) { "player index must be positive" }
        }
    }

    data class Rejected(val reason: ReservationRejection) : ReservationDecision
}
