package emu.server.world.entry

import emu.persistence.character.PlayerRecord
import emu.server.world.runtime.WorldReservationService
import emu.server.session.AccountId
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/** Prepares a loaded character and reserved player index for entry into the world. */
class WorldEntry(
    private val worldReservations: WorldReservationService,
    capacity: Int,
    private val loadCharacter: suspend (Long) -> PlayerRecord?,
) {
    private val permits = Semaphore(capacity)
    private val prepared = ConcurrentHashMap<GameSessionToken, PlayerRecord>()

    suspend fun prepare(accountId: AccountId): ReservationDecision {
        if (!permits.tryAcquire()) return ReservationDecision.Rejected(ReservationRejection.CAPACITY)
        val player =
            try {
                loadCharacter(accountId.value)
            } catch (failure: Throwable) {
                permits.release()
                throw failure
            }
        if (player == null) {
            permits.release()
            return ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE)
        }

        val token = GameSessionToken(UUID.randomUUID().toString())
        val decision =
            try {
                worldReservations.reserve(accountId.value, token)
            } catch (failure: Throwable) {
                withContext(NonCancellable) { worldReservations.release(token) }
                permits.release()
                throw failure
            }
        if (decision is ReservationDecision.Accepted) prepared[token] = player else permits.release()
        return decision
    }

    suspend fun cancel(token: GameSessionToken) {
        try {
            withContext(NonCancellable) { worldReservations.release(token) }
        } finally {
            if (prepared.remove(token) != null) permits.release()
        }
    }

    fun claim(token: GameSessionToken): PlayerRecord? = prepared.remove(token)

    fun finish() = permits.release()
}
