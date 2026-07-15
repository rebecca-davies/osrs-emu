package emu.server

import emu.server.game.GameServer
import emu.server.gateway.GatewayRouteHandler
import emu.server.gateway.GatewayRoutes
import emu.server.js5.Js5Server
import emu.server.login.LoginServer
import emu.protocol.osrs239.js5.prot.Js5Prot
import emu.protocol.osrs239.login.prot.LoginProt
import emu.server.session.AuthenticationCompletion
import emu.server.session.AuthenticationRejection
import emu.server.session.ConnectionHandoff
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.withTimeout

/** Coordinates stage handoffs without placing peer-service knowledge in the gateway. */
class ServerCoordinator(
    private val js5: Js5Server,
    private val login: LoginServer,
    private val game: GameServer,
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
        val reservation = withTimeout(config.admissionTimeout) { game.reserve(session.connection.principal) }
        val completion = reservation.toAuthenticationCompletion()
        val accepted = reservation as? ReservationDecision.Accepted
        var transferred = false
        try {
            if (!login.complete(write, session, completion)) return
            val slot = accepted ?: return
            transferred = true
            game.play(read, write, ConnectionHandoff(session.connection, slot))
        } finally {
            if (accepted != null && !transferred) game.release(accepted.token)
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
                    ReservationRejection.CAPACITY,
                    ReservationRejection.UNAVAILABLE -> AuthenticationRejection.WORLD_FULL
                },
            )
    }
