package emu.server.game.world

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.server.game.network.output.GameOutputSink
import emu.server.game.world.entry.WorldAttachment
import emu.server.session.account.AccountPrivilege
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class WorldTest {
    @Test
    fun `one session token cannot replace another reserved player`() {
        val world = testWorld(maxPlayerIndex = 2)
        val token = GameSessionToken("shared")

        assertEquals(ReservationDecision.Accepted(token, 1), world.reserve(1, token))
        assertEquals(
            ReservationDecision.Rejected(ReservationRejection.DUPLICATE),
            world.reserve(2, token),
        )
        world.release(token)

        val replacement = GameSessionToken("replacement")
        assertEquals(ReservationDecision.Accepted(replacement, 1), world.reserve(2, replacement))
    }

    @Test
    fun `one failed staged player is rejected without losing the remaining batch`() = runBlocking {
        var sessions = 0
        val world =
            testWorld(maxPlayerIndex = 2) {
                if (sessions++ == 0) error("bad persisted session")
                0L
            }
        val failedToken = GameSessionToken("failed")
        val healthyToken = GameSessionToken("healthy")
        assertEquals(ReservationDecision.Accepted(failedToken, 1), world.reserve(1, failedToken))
        assertEquals(ReservationDecision.Accepted(healthyToken, 2), world.reserve(2, healthyToken))
        val failedAttachment = WorldAttachment()
        val healthyAttachment = WorldAttachment()
        world.stageLogin(
            failedToken,
            player(1),
            AccountPrivilege.PLAYER,
            IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
            GameOutputSink { true },
            failedAttachment,
        )
        world.stageLogin(
            healthyToken,
            player(2),
            AccountPrivilege.PLAYER,
            IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
            GameOutputSink { true },
            healthyAttachment,
        )

        world.enterPendingPlayers()

        assertEquals(null, failedAttachment.login.await())
        assertEquals(2, assertNotNull(healthyAttachment.login.await()).playerIndex)
        assertEquals(listOf(2L), world.allPlayers().map { it.player.id })
        val replacement = GameSessionToken("replacement")
        assertEquals(ReservationDecision.Accepted(replacement, 1), world.reserve(1, replacement))
    }

    private fun player(id: Long) =
        CharacterRecord(
            id,
            "Player$id",
            CharacterPosition(3_200, 3_200, 0),
            0,
        )
}
