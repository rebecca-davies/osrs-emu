package emu.server.world

import emu.crypto.IsaacCipher
import emu.server.session.AccountId
import emu.server.session.ConnectionHandoff
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import emu.server.world.entry.WorldEntry
import emu.server.world.network.GameConnectionRunner
import emu.server.world.runtime.WorldLifecycle
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
        check(started.compareAndSet(false, true)) { "world server can only be started once" }
        worldLifecycle.start()
    }

    override suspend fun awaitTermination() {
        worldLifecycle.awaitTermination()
    }

    override suspend fun prepare(accountId: AccountId): ReservationDecision {
        if (!worldLifecycle.isRunning) {
            return ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE)
        }
        return entries.prepare(accountId)
    }

    override suspend fun play(
        read: ByteReadChannel,
        write: ByteWriteChannel,
        handoff: ConnectionHandoff,
        beginSession: suspend (Int) -> Boolean,
    ): Boolean {
        val token = handoff.reservation.token
        val accountId = handoff.accountId
        if (!worldLifecycle.isRunning) {
            entries.cancel(token)
            return false
        }
        val player = entries.claim(token)
        if (player == null) {
            entries.cancel(token)
            return false
        }
        try {
            if (player.id != accountId.value) {
                entries.cancel(token)
                return false
            }
            val seeds = handoff.isaac.toIntArray()
            return connections.run(
                read,
                write,
                IsaacCipher(seeds),
                IsaacCipher(IntArray(seeds.size) { seeds[it] + OUTBOUND_ISAAC_OFFSET }),
                player,
                token,
                beginSession,
            )
        } finally {
            entries.finish()
        }
    }

    override suspend fun stop() {
        worldLifecycle.stop()
    }

    private companion object {
        const val OUTBOUND_ISAAC_OFFSET = 50
    }
}
