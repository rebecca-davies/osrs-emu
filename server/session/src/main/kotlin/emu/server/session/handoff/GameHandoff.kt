package emu.server.session.handoff

import emu.server.session.account.AuthenticatedAccount
import emu.server.session.authentication.IsaacBootstrap

/** Opaque key joining login reservation and world handoff. */
@JvmInline
value class GameSessionToken(val value: String) {
    init {
        require(value.isNotBlank()) { "game session token must not be blank" }
    }
}

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

/** Reason a world reservation was rejected. */
enum class ReservationRejection {
    DUPLICATE,
    CAPACITY,
    UNAVAILABLE,
}

/** Exact login-owned values transferred to the game service after world entry is accepted. */
data class ConnectionHandoff(
    val account: AuthenticatedAccount,
    val isaac: IsaacBootstrap,
    val reservation: ReservationDecision.Accepted,
)
