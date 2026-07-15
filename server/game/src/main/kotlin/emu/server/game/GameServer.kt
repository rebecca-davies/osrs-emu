package emu.server.game

import emu.server.session.AuthenticatedPrincipal
import emu.server.session.ConnectionHandoff
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/** Character admission, connection handoff, and lifecycle operations owned by the game service. */
interface GameServer : AutoCloseable {
    fun start()

    suspend fun reserve(principal: AuthenticatedPrincipal): ReservationDecision

    suspend fun release(token: GameSessionToken)

    suspend fun play(read: ByteReadChannel, write: ByteWriteChannel, handoff: ConnectionHandoff)

    suspend fun stop()
}
