package emu.server.game

import emu.server.game.network.connection.GameConnectionRunner
import emu.server.game.runtime.lifecycle.WorldLifecycle
import emu.server.game.world.entry.WorldEntry
import emu.server.session.account.AccountId
import emu.server.session.handoff.ConnectionHandoff
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.util.concurrent.atomic.AtomicBoolean

/** Thin game-service boundary over world entry, connection IO, and one authoritative world. */
class GameServer(
    private val entries: WorldEntry,
    private val connections: GameConnectionRunner,
    private val worldLifecycle: WorldLifecycle,
) : GameService {
    private val started = AtomicBoolean(false)

    override fun start() {
        check(started.compareAndSet(false, true)) { "game server can only be started once" }
        worldLifecycle.start()
    }

    override suspend fun awaitTermination() {
        worldLifecycle.awaitTermination()
    }

    override suspend fun reserve(accountId: AccountId): ReservationDecision {
        if (!worldLifecycle.isRunning) {
            return ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE)
        }
        return entries.reserve(accountId)
    }

    override suspend fun enter(
        read: ByteReadChannel,
        write: ByteWriteChannel,
        handoff: ConnectionHandoff,
        completeLogin: suspend (Int) -> Boolean,
    ): Boolean {
        val token = handoff.reservation.token
        val account = handoff.account
        if (!worldLifecycle.isRunning) {
            entries.cancel(token)
            return false
        }
        val character = entries.claim(token)
        if (character == null) {
            entries.cancel(token)
            return false
        }
        try {
            if (character.id != account.accountId.value) {
                entries.cancel(token)
                return false
            }
            return connections.run(
                read,
                write,
                character,
                handoff,
                completeLogin,
            )
        } finally {
            entries.finish()
        }
    }

    override suspend fun stop() {
        worldLifecycle.stop()
    }
}
