package emu.server.game.network

import emu.game.pathfinding.PlayerRouteRequestSink
import emu.game.ui.PlayerButtonSink
import emu.game.chat.PlayerChatSink
import emu.compression.HuffmanCodec
import emu.server.game.network.handler.IfButtonXHandler
import emu.server.game.network.handler.MoveGameClickHandler
import emu.server.game.network.handler.MessagePublicHandler
import emu.server.game.network.handler.SetChatFilterSettingsHandler
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.game.message.MoveGameClick
import emu.protocol.osrs239.game.message.IfButtonX
import emu.protocol.osrs239.game.message.MessagePublic
import emu.protocol.osrs239.game.message.SetChatFilterSettings

/** Binds per-connection game handlers whose mutable mailbox dependency cannot be a singleton. */
fun HandlerRepositoryBuilder.installGameHandlers(
    routeRequests: PlayerRouteRequestSink,
    buttons: PlayerButtonSink,
    chat: PlayerChatSink,
    huffman: HuffmanCodec,
): HandlerRepositoryBuilder =
    bind(MoveGameClick::class.java, MoveGameClickHandler(routeRequests))
        .bind(IfButtonX::class.java, IfButtonXHandler(buttons))
        .bind(MessagePublic::class.java, MessagePublicHandler(huffman, chat))
        .bind(SetChatFilterSettings::class.java, SetChatFilterSettingsHandler(chat))
