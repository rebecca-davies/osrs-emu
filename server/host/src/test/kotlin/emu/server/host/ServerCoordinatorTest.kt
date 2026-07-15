package emu.server.host

import emu.server.world.WorldServer
import emu.server.js5.Js5Server
import emu.server.login.LoginServer
import emu.server.session.AccountPrivilege
import emu.server.session.AuthenticatedPrincipal
import emu.server.session.AuthenticatedSession
import emu.server.session.AuthenticationCompletion
import emu.server.session.ConnectionBootstrap
import emu.server.session.ConnectionHandoff
import emu.server.session.GameSessionToken
import emu.server.session.IsaacBootstrap
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ServerCoordinatorTest {
    @Test
    fun `accepted login completes after reservation and then transfers to world`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events)
        val world = FakeWorld(events, ACCEPTED)
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel())

        assertEquals(listOf("authenticate", "reserve", "complete", "play"), events)
        assertIs<AuthenticationCompletion.Accepted>(login.completion)
        assertEquals(0, world.releaseCount)
    }

    @Test
    fun `rejected reservation completes login rejection without world handoff`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, completionAccepted = false)
        val world = FakeWorld(events, ReservationDecision.Rejected(ReservationRejection.DUPLICATE))
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel())

        assertEquals(listOf("authenticate", "reserve", "complete"), events)
        assertFalse(world.played)
        assertEquals(0, world.releaseCount)
    }

    @Test
    fun `failed login completion releases accepted reservation exactly once`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, failCompletion = true)
        val world = FakeWorld(events, ACCEPTED)
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        val failure = runCatching { coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel()) }

        assertTrue(failure.isFailure)
        assertFalse(world.played)
        assertEquals(1, world.releaseCount)
        assertEquals(listOf("authenticate", "reserve", "complete", "release"), events)
    }

    private class FakeLogin(
        private val events: MutableList<String>,
        private val completionAccepted: Boolean = true,
        private val failCompletion: Boolean = false,
    ) : LoginServer {
        var completion: AuthenticationCompletion? = null

        override suspend fun authenticate(read: ByteReadChannel, write: ByteWriteChannel): AuthenticatedSession {
            events += "authenticate"
            return SESSION
        }

        override suspend fun complete(
            write: ByteWriteChannel,
            login: AuthenticatedSession,
            completion: AuthenticationCompletion,
        ): Boolean {
            events += "complete"
            this.completion = completion
            if (failCompletion) error("completion failed")
            return completionAccepted
        }

        override fun close() = Unit
    }

    private class FakeWorld(
        private val events: MutableList<String>,
        private val decision: ReservationDecision,
    ) : WorldServer {
        var played = false
        var releaseCount = 0

        override fun start() = Unit

        override suspend fun awaitTermination() = Unit

        override suspend fun reserve(principal: AuthenticatedPrincipal): ReservationDecision {
            events += "reserve"
            return decision
        }

        override suspend fun release(token: GameSessionToken) {
            events += "release"
            releaseCount++
        }

        override suspend fun play(read: ByteReadChannel, write: ByteWriteChannel, handoff: ConnectionHandoff) {
            events += "play"
            played = true
        }

        override suspend fun stop() = Unit

        override fun close() = Unit
    }

    private object FakeJs5 : Js5Server {
        override suspend fun serve(read: ByteReadChannel, write: ByteWriteChannel) = Unit

        override fun close() = Unit
    }

    private companion object {
        val TOKEN = GameSessionToken("reservation")
        val ACCEPTED = ReservationDecision.Accepted(TOKEN, playerIndex = 1)
        val PRINCIPAL = AuthenticatedPrincipal(1, "test player", "Test_Player", AccountPrivilege.PLAYER)
        val SESSION = AuthenticatedSession(ConnectionBootstrap(IsaacBootstrap(1, 2, 3, 4), PRINCIPAL), reconnect = false)
    }
}
