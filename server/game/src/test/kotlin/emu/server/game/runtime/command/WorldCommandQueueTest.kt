package emu.server.game.runtime.command

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.server.game.network.output.GameOutputSink
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.testWorld
import emu.server.session.account.AccountPrivilege
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class WorldCommandQueueTest {
    @Test
    fun `reservations duplicate-check and allocate bounded world indexes`() = runBlocking {
        val world = testWorld(maxPlayerIndex = 2)
        val queue = WorldCommandQueue(capacity = 8)
        val firstToken = GameSessionToken("first")
        val secondToken = GameSessionToken("second")
        val first = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(1, firstToken) }
        val duplicate = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(1, secondToken) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(2, secondToken) }

        queue.drain(world)

        assertEquals(ReservationDecision.Accepted(firstToken, 1), first.await())
        assertEquals(
            ReservationDecision.Rejected(ReservationRejection.DUPLICATE),
            duplicate.await(),
        )
        assertEquals(ReservationDecision.Accepted(secondToken, 2), second.await())
    }

    @Test
    fun `attachment remains inactive until login output activates it`() = runBlocking {
        val world = testWorld(maxPlayerIndex = 1)
        val queue = WorldCommandQueue(capacity = 8)
        val token = GameSessionToken("player")
        val reservation = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(1, token) }
        queue.drain(world)
        assertEquals(ReservationDecision.Accepted(token, 1), reservation.await())
        val attachment =
            queue.attach(
                token,
                player(1),
                AccountPrivilege.PLAYER,
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )

        queue.drain(world)
        assertFalse(attachment.login.isCompleted)
        world.enterPendingPlayers()
        assertEquals(1, attachment.login.await()?.playerIndex)
        assertTrue(world.activePlayers().isEmpty())

        queue.activate(token)
        queue.drain(world)
        world.activateTestPlayer(token)

        assertEquals(listOf(1L), world.activePlayers().map { it.id })
    }

    @Test
    fun `commands from an old connection cannot affect a replacement session`() = runBlocking {
        val world = testWorld(maxPlayerIndex = 1)
        val queue = WorldCommandQueue(capacity = 8)
        val oldToken = GameSessionToken("old")
        val currentToken = GameSessionToken("current")
        val old =
            world.addTestPlayer(
                player(1),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
                sessionToken = oldToken,
            )
        world.remove(old)
        val current =
            world.addTestPlayer(
                player(1),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
                sessionToken = currentToken,
            )

        queue.activate(oldToken)
        queue.disconnect(oldToken)
        queue.drain(world)

        assertFalse(current.active)
        assertFalse(current.logoutRequested)

        queue.activate(currentToken)
        queue.drain(world)
        world.activateTestPlayer(currentToken)
        assertTrue(current.active)

        queue.disconnect(currentToken)
        queue.drain(world)
        assertTrue(current.logoutRequested)
        assertFalse(world.session(current).isConnected)
    }

    @Test
    fun `one cycle processes only its configured command budget`() = runBlocking {
        val world = testWorld(maxPlayerIndex = 3)
        val queue =
            WorldCommandQueue(
                WorldCommandQueueConfig(capacity = 3, maxPerCycle = 2),
            )
        val first = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(1, GameSessionToken("first")) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(2, GameSessionToken("second")) }
        val third = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(3, GameSessionToken("third")) }

        queue.drain(world)

        assertEquals(ReservationDecision.Accepted(GameSessionToken("first"), 1), first.await())
        assertEquals(ReservationDecision.Accepted(GameSessionToken("second"), 2), second.await())
        assertFalse(third.isCompleted)
        queue.drain(world)
        assertEquals(ReservationDecision.Accepted(GameSessionToken("third"), 3), third.await())
    }

    @Test
    fun `closing rejects queued and future reservations without leaking world indexes`() = runBlocking {
        val world = testWorld(maxPlayerIndex = 3)
        val queue =
            WorldCommandQueue(
                WorldCommandQueueConfig(capacity = 3, maxPerCycle = 1),
            )
        val reservations =
            (1L..3L).map { id ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    queue.reserve(id, GameSessionToken("token$id"))
                }
            }

        queue.close(world)

        reservations.forEach {
            assertEquals(
                ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE),
                it.await(),
            )
        }
        assertEquals(
            ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE),
            queue.reserve(4, GameSessionToken("late")),
        )

        val replacement = WorldCommandQueue(capacity = 1)
        val reservation = async(start = CoroutineStart.UNDISPATCHED) {
            replacement.reserve(4, GameSessionToken("replacement"))
        }
        replacement.drain(world)
        assertEquals(
            ReservationDecision.Accepted(GameSessionToken("replacement"), 1),
            reservation.await(),
        )
    }

    @Test
    fun `closing rejects queued attachment but still applies disconnect cleanup`() = runBlocking {
        val world = testWorld(maxPlayerIndex = 1)
        val queue = WorldCommandQueue(capacity = 4)
        val token = GameSessionToken("player")
        val reservation = async(start = CoroutineStart.UNDISPATCHED) { queue.reserve(1, token) }
        queue.drain(world)
        assertTrue(reservation.await() is ReservationDecision.Accepted)
        val attachment =
            queue.attach(
                token,
                player(1),
                AccountPrivilege.PLAYER,
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )

        queue.close(world)

        assertEquals(null, attachment.login.await())
        assertTrue(world.allPlayers().isEmpty())
    }

    private fun player(id: Long) =
        CharacterRecord(
            id,
            "Player$id",
            CharacterPosition(3222, 3218, 0),
            0,
        )
}
