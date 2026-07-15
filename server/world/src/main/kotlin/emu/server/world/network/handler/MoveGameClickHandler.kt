package emu.server.world.network.handler

import emu.game.pathfinding.PlayerRouteRequestSink
import emu.game.pathfinding.RouteRequestAdmission
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.MoveGameClick
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Admits a decoded click to the bounded network-to-world mailbox.
 *
 * It deliberately performs no path search and mutates no player state: both happen later on the
 * authoritative world cycle's client-input/player phases.
 */
class MoveGameClickHandler(
    private val routeRequests: PlayerRouteRequestSink,
) : PacketHandler<MoveGameClick> {
    override suspend fun handle(message: MoveGameClick, ctx: HandlerContext) {
        when (routeRequests.submit(message.x, message.z, message.keyCombination)) {
            RouteRequestAdmission.QUEUED -> Unit
            RouteRequestAdmission.REPLACED -> logger.debug { "coalesced pending player route request" }
            RouteRequestAdmission.REJECTED -> logger.warn { "rejected player route request outside world bounds" }
        }
    }
}
