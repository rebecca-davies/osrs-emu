package emu.server.world.admission

import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.server.world.runtime.WorldRuntime
import emu.server.session.AccountPrivilege
import emu.server.session.AuthenticatedPrincipal
import emu.server.session.ReservationDecision
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class GameAdmissionTest {
    @Test
    fun `cancelled reservation releases its queued world slot and session permit`() = runBlocking {
        val world = WorldRuntime(tickInterval = 1.milliseconds, maxPlayerIndex = 1)
        val admission = GameAdmission(world, capacity = 1) { PLAYER }
        val cancelled = launch { admission.reserve(PRINCIPAL) }
        yield()
        cancelled.cancelAndJoin()

        val worldJob = launch { world.run() }
        val retried = admission.reserve(PRINCIPAL)

        assertIs<ReservationDecision.Accepted>(retried)
        admission.release(retried.token)
        worldJob.cancelAndJoin()
    }

    private companion object {
        val PRINCIPAL = AuthenticatedPrincipal(1, "test player", "Test_Player", AccountPrivilege.PLAYER)
        val PLAYER = PlayerRecord(1, "test player", "Test_Player", PlayerPosition(3222, 3218, 0), 0)
    }
}
