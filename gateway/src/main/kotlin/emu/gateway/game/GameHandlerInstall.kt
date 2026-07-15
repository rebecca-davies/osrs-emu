package emu.gateway.game

import emu.game.pathfinding.PlayerRouteRequestSink
import emu.game.ui.PlayerButtonSink
import emu.gateway.game.handler.IfButtonXHandler
import emu.gateway.game.handler.MoveGameClickHandler
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.game.message.MoveGameClick
import emu.protocol.osrs239.game.message.IfButtonX

/** Binds per-connection game handlers whose mutable mailbox dependency cannot be a singleton. */
fun HandlerRepositoryBuilder.installGameHandlers(
    routeRequests: PlayerRouteRequestSink,
    buttons: PlayerButtonSink,
): HandlerRepositoryBuilder =
    bind(MoveGameClick::class.java, MoveGameClickHandler(routeRequests))
        .bind(IfButtonX::class.java, IfButtonXHandler(buttons))
