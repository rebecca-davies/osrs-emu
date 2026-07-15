package emu.server.world

import emu.server.session.AccountId
import emu.server.session.ConnectionHandoff
import emu.server.session.ReservationDecision
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/** World entry, connection handoff, and lifecycle operations exposed to the host coordinator. */
interface GameService {
    fun start()

    /** Suspends for the server lifetime and propagates an unexpected world failure. */
    suspend fun awaitTermination()

    suspend fun prepare(accountId: AccountId): ReservationDecision

    /** Consumes the accepted reservation on every return or failure path. */
    suspend fun play(
        read: ByteReadChannel,
        write: ByteWriteChannel,
        handoff: ConnectionHandoff,
        beginSession: suspend (Int) -> Boolean,
    ): Boolean

    suspend fun stop()
}
