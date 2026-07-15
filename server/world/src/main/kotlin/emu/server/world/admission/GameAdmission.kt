package emu.server.world.admission

import emu.persistence.character.PlayerRecord
import emu.server.world.runtime.WorldReservationService
import emu.server.session.AuthenticatedPrincipal
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/** Preloads characters and owns reservation permits until their game sessions finish. */
internal class GameAdmission(
    private val worldReservations: WorldReservationService,
    capacity: Int,
    private val loadCharacter: suspend (Long) -> PlayerRecord?,
) {
    private val permits = Semaphore(capacity)
    private val prepared = ConcurrentHashMap<GameSessionToken, PlayerRecord>()

    suspend fun reserve(principal: AuthenticatedPrincipal): ReservationDecision {
        if (!permits.tryAcquire()) return ReservationDecision.Rejected(ReservationRejection.CAPACITY)
        val player =
            try {
                loadCharacter(principal.accountId)
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
                worldReservations.reserve(principal.accountId, token)
            } catch (failure: Throwable) {
                withContext(NonCancellable) { worldReservations.release(token) }
                permits.release()
                throw failure
            }
        if (decision is ReservationDecision.Accepted) prepared[token] = player else permits.release()
        return decision
    }

    suspend fun release(token: GameSessionToken) {
        worldReservations.release(token)
        if (prepared.remove(token) != null) permits.release()
    }

    fun take(token: GameSessionToken): PlayerRecord? = prepared.remove(token)

    fun finishSession() = permits.release()
}
