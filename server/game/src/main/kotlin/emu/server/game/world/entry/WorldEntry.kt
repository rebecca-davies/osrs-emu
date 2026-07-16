package emu.server.game.world.entry

import emu.persistence.character.model.CharacterRecord
import emu.server.session.account.AccountId
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/** Loads a character and reserves its player index before entry into the world. */
class WorldEntry(
    private val worldReservations: WorldReservations,
    capacity: Int,
    private val loadCharacter: suspend (Long) -> CharacterRecord?,
) {
    private val permits = Semaphore(capacity)
    private val prepared = ConcurrentHashMap<GameSessionToken, CharacterRecord>()

    suspend fun reserve(accountId: AccountId): ReservationDecision {
        if (!permits.tryAcquire()) return ReservationDecision.Rejected(ReservationRejection.CAPACITY)
        val character =
            try {
                loadCharacter(accountId.value)
            } catch (failure: Throwable) {
                permits.release()
                throw failure
            }
        if (character == null) {
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
        if (decision is ReservationDecision.Accepted) prepared[token] = character else permits.release()
        return decision
    }

    suspend fun cancel(token: GameSessionToken) {
        try {
            withContext(NonCancellable) { worldReservations.release(token) }
        } finally {
            if (prepared.remove(token) != null) permits.release()
        }
    }

    fun claim(token: GameSessionToken): CharacterRecord? = prepared.remove(token)

    fun finish() = permits.release()
}
