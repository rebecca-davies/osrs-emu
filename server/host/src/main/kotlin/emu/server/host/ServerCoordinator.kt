package emu.server.host

import emu.server.world.GameService
import emu.server.gateway.GatewayRouteHandler
import emu.server.gateway.GatewayRoutes
import emu.server.js5.Js5Service
import emu.server.login.LoginService
import emu.protocol.osrs239.js5.prot.Js5Prot
import emu.protocol.osrs239.login.prot.LoginProt
import emu.server.session.AuthenticationCompletion
import emu.server.session.AuthenticationRejection
import emu.server.session.ConnectionHandoff
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.withTimeoutOrNull

/** Coordinates stage handoffs without placing peer-service knowledge in the gateway. */
class ServerCoordinator(
    private val js5: Js5Service,
    private val login: LoginService,
    private val world: GameService,
    private val config: CoordinatorConfig,
) {
    fun gatewayRoutes(): GatewayRoutes =
        GatewayRoutes(
            js5Opcode = Js5Prot.HANDSHAKE.opcode,
            loginOpcode = LoginProt.INIT.opcode,
            js5 = GatewayRouteHandler(js5::serve),
            login = GatewayRouteHandler(::loginAndTransfer),
        )

    private suspend fun loginAndTransfer(
        read: ByteReadChannel,
        write: ByteWriteChannel,
    ) {
        val session = login.authenticate(read, write) ?: return
        val reservation =
            withTimeoutOrNull(config.worldEntryTimeout) { world.prepare(session.account.accountId) }
                ?: ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE)
        val completion = reservation.toAuthenticationCompletion()
        val accepted = reservation as? ReservationDecision.Accepted
        if (accepted == null) {
            login.complete(write, session, completion)
            return
        }
        var completionWritten = false
        val attached =
            world.play(
                read,
                write,
                ConnectionHandoff(session.account.accountId, session.isaac, accepted),
            ) { playerIndex ->
                check(playerIndex == accepted.playerIndex) { "world changed the reserved player index" }
                completionWritten = true
                login.complete(
                    write,
                    session,
                    AuthenticationCompletion.Accepted(playerIndex),
                )
            }
        if (!attached && !completionWritten) {
            login.complete(
                write,
                session,
                AuthenticationCompletion.Rejected(AuthenticationRejection.WORLD_UNAVAILABLE),
            )
        }
    }
}

private fun ReservationDecision.toAuthenticationCompletion(): AuthenticationCompletion =
    when (this) {
        is ReservationDecision.Accepted -> AuthenticationCompletion.Accepted(playerIndex)
        is ReservationDecision.Rejected ->
            AuthenticationCompletion.Rejected(
                when (reason) {
                    ReservationRejection.DUPLICATE -> AuthenticationRejection.ALREADY_ONLINE
                    ReservationRejection.CAPACITY -> AuthenticationRejection.WORLD_FULL
                    ReservationRejection.UNAVAILABLE -> AuthenticationRejection.WORLD_UNAVAILABLE
                },
            )
    }
