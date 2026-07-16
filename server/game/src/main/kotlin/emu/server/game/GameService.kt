package emu.server.game

import emu.server.session.account.AccountId
import emu.server.session.handoff.ConnectionHandoff
import emu.server.session.handoff.ReservationDecision
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/** Game-server world entry, connection handoff, and lifecycle operations exposed to the host. */
interface GameService {
    fun start()

    /** Suspends for the server lifetime and propagates an unexpected world failure. */
    suspend fun awaitTermination()

    suspend fun reserve(accountId: AccountId): ReservationDecision

    /** Consumes the accepted reservation on every return or failure path. */
    suspend fun enter(
        read: ByteReadChannel,
        write: ByteWriteChannel,
        handoff: ConnectionHandoff,
        completeLogin: suspend (Int) -> Boolean,
    ): Boolean

    suspend fun stop()
}
