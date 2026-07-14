package emu.gateway.game

import emu.game.pathfinding.PlayerRouteRequestSink
import emu.gateway.game.handler.MoveGameClickHandler
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.game.message.MoveGameClick

/** Binds per-connection game handlers whose mutable mailbox dependency cannot be a singleton. */
fun HandlerRepositoryBuilder.installGameHandlers(
    routeRequests: PlayerRouteRequestSink,
): HandlerRepositoryBuilder =
    bind(MoveGameClick::class.java, MoveGameClickHandler(routeRequests))
