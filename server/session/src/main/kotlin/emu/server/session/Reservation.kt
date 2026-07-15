package emu.server.session

/** Opaque key joining login reservation and world handoff. */
@JvmInline
value class GameSessionToken(val value: String) {
    init {
        require(value.isNotBlank()) { "game session token must not be blank" }
    }
}

/** Reason a world reservation was rejected. */
enum class ReservationRejection {
    DUPLICATE,
    CAPACITY,
    UNAVAILABLE,
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
