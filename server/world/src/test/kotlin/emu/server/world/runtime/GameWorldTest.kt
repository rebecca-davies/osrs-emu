package emu.server.world.runtime

import emu.game.action.GameInputQueue
import emu.game.action.GameInputQueueConfig
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import emu.server.world.network.GameOutputSink
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GameWorldTest {
    @Test
    fun `one session token cannot replace another reserved player`() {
        val world = testGameWorld(maxPlayerIndex = 2)
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
            testGameWorld(maxPlayerIndex = 2) {
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
            GameInputQueue(GameInputQueueConfig()),
            GameOutputSink { true },
            failedAttachment,
        )
        world.stageLogin(
            healthyToken,
            player(2),
            GameInputQueue(GameInputQueueConfig()),
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
        PlayerRecord(
            id,
            "Player$id",
            PlayerPosition(3_200, 3_200, 0),
            0,
        )
}
