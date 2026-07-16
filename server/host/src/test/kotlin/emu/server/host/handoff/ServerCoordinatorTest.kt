package emu.server.host.handoff

import emu.server.game.GameService
import emu.server.host.config.CoordinatorConfig
import emu.server.js5.Js5Service
import emu.server.login.LoginService
import emu.server.session.account.AccountId
import emu.server.session.account.AccountPrivilege
import emu.server.session.account.AuthenticatedAccount
import emu.server.session.authentication.AuthenticatedSession
import emu.server.session.authentication.AuthenticationCompletion
import emu.server.session.authentication.AuthenticationRejection
import emu.server.session.authentication.IsaacBootstrap
import emu.server.session.handoff.ConnectionHandoff
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

class ServerCoordinatorTest {
    @Test
    fun `accepted login completes after world entry preparation and then transfers`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events)
        val game = FakeGame(events, ACCEPTED)
        val coordinator = ServerCoordinator(FakeJs5, login, game, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.serve(ByteChannel(), ByteChannel())

        assertEquals(listOf("authenticate", "prepare", "play", "complete"), events)
        assertIs<AuthenticationCompletion.Accepted>(login.completion)
        assertEquals(ACCOUNT, game.handoff?.account)
        assertEquals(SESSION.isaac, game.handoff?.isaac)
    }

    @Test
    fun `rejected world entry completes login rejection without handoff`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, completionAccepted = false)
        val game = FakeGame(events, ReservationDecision.Rejected(ReservationRejection.DUPLICATE))
        val coordinator = ServerCoordinator(FakeJs5, login, game, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.serve(ByteChannel(), ByteChannel())

        assertEquals(listOf("authenticate", "prepare", "complete"), events)
        assertFalse(game.played)
    }

    @Test
    fun `unavailable world is not reported as a full world`() = runBlocking {
        val login = FakeLogin(mutableListOf(), completionAccepted = false)
        val game = FakeGame(mutableListOf(), ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE))
        val coordinator = ServerCoordinator(FakeJs5, login, game, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.serve(ByteChannel(), ByteChannel())

        assertEquals(
            AuthenticationCompletion.Rejected(AuthenticationRejection.WORLD_UNAVAILABLE),
            login.completion,
        )
    }

    @Test
    fun `failed login completion is cleaned by the world after handoff`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, failCompletion = true)
        val game = FakeGame(events, ACCEPTED)
        val coordinator = ServerCoordinator(FakeJs5, login, game, CoordinatorConfig(1.seconds))

        val failure = runCatching { coordinator.gatewayRoutes().login.serve(ByteChannel(), ByteChannel()) }

        assertTrue(failure.isFailure)
        assertTrue(game.played)
        assertEquals(listOf("authenticate", "prepare", "play", "complete"), events)
    }

    @Test
    fun `shutdown attachment rejection never writes login success`() = runBlocking {
        val events = mutableListOf<String>()
        val login = FakeLogin(events, completionAccepted = false)
        val game = FakeGame(events, ACCEPTED, attach = false)
        val coordinator = ServerCoordinator(FakeJs5, login, game, CoordinatorConfig(1.seconds))

        coordinator.gatewayRoutes().login.serve(ByteChannel(), ByteChannel())

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

    private class FakeGame(
        private val events: MutableList<String>,
        private val decision: ReservationDecision,
        private val attach: Boolean = true,
    ) : GameService {
        var played = false
        var handoff: ConnectionHandoff? = null

        override fun start() = Unit

        override suspend fun awaitTermination() = Unit

        override suspend fun reserve(accountId: AccountId): ReservationDecision {
            events += "prepare"
            return decision
        }

        override suspend fun enter(
            read: ByteReadChannel,
            write: ByteWriteChannel,
            handoff: ConnectionHandoff,
            completeLogin: suspend (Int) -> Boolean,
        ): Boolean {
            events += "play"
            played = true
            this.handoff = handoff
            if (!attach) return false
            val playerIndex = (decision as ReservationDecision.Accepted).playerIndex
            return completeLogin(playerIndex)
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
