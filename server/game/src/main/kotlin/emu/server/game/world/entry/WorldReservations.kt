package emu.server.game.world.entry

import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision

/** Reserves and releases world capacity before the login service reports success. */
interface WorldReservations {
    suspend fun reserve(playerId: Long, token: GameSessionToken): ReservationDecision

    suspend fun release(token: GameSessionToken)
}
