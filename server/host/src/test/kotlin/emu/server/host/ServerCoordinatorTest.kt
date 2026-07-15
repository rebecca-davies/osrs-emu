package emu.server.host

import emu.server.world.GameService
import emu.server.js5.Js5Service
import emu.server.login.LoginService
import emu.server.session.AccountId
import emu.server.session.AccountPrivilege
import emu.server.session.AuthenticatedAccount
import emu.server.session.AuthenticatedSession
import emu.server.session.AuthenticationCompletion
import emu.server.session.AuthenticationRejection
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
    fun `accepted login completes after world entry preparation and then transfers`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events)
        val world = FakeWorld(events, ACCEPTED)
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel())

        assertEquals(listOf("authenticate", "prepare", "play", "complete"), events)
        assertIs<AuthenticationCompletion.Accepted>(login.completion)
        assertEquals(ACCOUNT.accountId, world.handoff?.accountId)
        assertEquals(SESSION.isaac, world.handoff?.isaac)
    }

    @Test
    fun `rejected world entry completes login rejection without handoff`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, completionAccepted = false)
        val world = FakeWorld(events, ReservationDecision.Rejected(ReservationRejection.DUPLICATE))
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel())

        assertEquals(listOf("authenticate", "prepare", "complete"), events)
        assertFalse(world.played)
    }

    @Test
    fun `unavailable world is not reported as a full world`() = runBlocking {
        val login = FakeLogin(mutableListOf(), completionAccepted = false)
        val world = FakeWorld(mutableListOf(), ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE))
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel())

        assertEquals(
            AuthenticationCompletion.Rejected(AuthenticationRejection.WORLD_UNAVAILABLE),
            login.completion,
        )
    }

    @Test
    fun `failed login completion is cleaned by the world after handoff`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, failCompletion = true)
        val world = FakeWorld(events, ACCEPTED)
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        val failure = runCatching { coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel()) }

        assertTrue(failure.isFailure)
        assertTrue(world.played)
        assertEquals(listOf("authenticate", "prepare", "play", "complete"), events)
    }

    @Test
    fun `shutdown attachment rejection never writes login success`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, completionAccepted = false)
        val world = FakeWorld(events, ACCEPTED, attach = false)
        val coordinator = ServerCoordinator(FakeJs5, login, world, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.handle(ByteChannel(), ByteChannel())

        assertEquals(listOf("authenticate", "prepare", "play", "complete"), events)
        assertEquals(
            AuthenticationCompletion.Rejected(AuthenticationRejection.WORLD_UNAVAILABLE),
            login.completion,
        )
    }

    private class FakeLogin(
        private val events: MutableList<String>,
        private val completionAccepted: Boolean = true,
        private val failCompletion: Boolean = false,
    ) : LoginService {
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
        private val attach: Boolean = true,
    ) : GameService {
        var played = false
        var handoff: ConnectionHandoff? = null

        override fun start() = Unit

        override suspend fun awaitTermination() = Unit

        override suspend fun prepare(accountId: AccountId): ReservationDecision {
            events += "prepare"
            return decision
        }

        override suspend fun play(
            read: ByteReadChannel,
            write: ByteWriteChannel,
            handoff: ConnectionHandoff,
            beginSession: suspend (Int) -> Boolean,
        ): Boolean {
            events += "play"
            played = true
            this.handoff = handoff
            if (!attach) return false
            val playerIndex = (decision as ReservationDecision.Accepted).playerIndex
            return beginSession(playerIndex)
        }

        override suspend fun stop() = Unit
    }

    private object FakeJs5 : Js5Service {
        override suspend fun serve(read: ByteReadChannel, write: ByteWriteChannel) = Unit

        override fun close() = Unit
    }

    private companion object {
        val TOKEN = GameSessionToken("reservation")
        val ACCEPTED = ReservationDecision.Accepted(TOKEN, playerIndex = 1)
        val ACCOUNT = AuthenticatedAccount(AccountId(1), AccountPrivilege.PLAYER)
        val SESSION = AuthenticatedSession(ACCOUNT, IsaacBootstrap(1, 2, 3, 4))
    }
}
