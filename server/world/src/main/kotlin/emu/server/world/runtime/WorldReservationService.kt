package emu.server.world.runtime

import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision

/** Reserves and releases world capacity before the login service reports success. */
interface WorldReservationService {
    suspend fun reserve(playerId: Long, token: GameSessionToken): ReservationDecision

    suspend fun release(token: GameSessionToken)
}
