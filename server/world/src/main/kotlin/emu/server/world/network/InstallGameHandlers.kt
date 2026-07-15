package emu.server.world.network

import emu.compression.HuffmanCodec
import emu.game.action.PlayerActionSink
import emu.server.world.network.handler.IfButtonXHandler
import emu.server.world.network.handler.MoveGameClickHandler
import emu.server.world.network.handler.MessagePublicHandler
import emu.server.world.network.handler.SetChatFilterSettingsHandler
import emu.transport.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.game.message.MoveGameClick
import emu.protocol.osrs239.game.message.IfButtonX
import emu.protocol.osrs239.game.message.MessagePublic
import emu.protocol.osrs239.game.message.SetChatFilterSettings

/** Installs per-connection game handlers against that connection's bounded action queue. */
fun HandlerRepositoryBuilder.installGameHandlers(
    actions: PlayerActionSink,
    huffman: HuffmanCodec,
): HandlerRepositoryBuilder =
    bind(MoveGameClick::class.java, MoveGameClickHandler(actions))
        .bind(IfButtonX::class.java, IfButtonXHandler(actions))
        .bind(MessagePublic::class.java, MessagePublicHandler(huffman, actions))
        .bind(SetChatFilterSettings::class.java, SetChatFilterSettingsHandler(actions))
