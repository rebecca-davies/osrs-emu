package emu.gateway.game.handler

import emu.game.pathfinding.PlayerRouteRequestSink
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.game.message.MoveGameClick

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
        routeRequests.submit(message.x, message.z, message.keyCombination)
    }
}
