package emu.server.game.network.input

import emu.compression.HuffmanCodec
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.message.chat.MessagePublic
import emu.protocol.osrs239.game.message.chat.SetChatFilterSettings
import emu.protocol.osrs239.game.message.client.ClientCheat
import emu.protocol.osrs239.game.message.component.IfButtonX
import emu.protocol.osrs239.game.message.movement.MoveGameClick
import emu.server.game.network.input.chat.MessagePublicHandler
import emu.server.game.network.input.chat.SetChatFilterSettingsHandler
import emu.server.game.network.input.client.ClientCheatHandler
import emu.server.game.network.input.movement.MoveGameClickHandler
import emu.server.game.network.input.ui.IfButtonXHandler
import emu.transport.pipeline.handler.HandlerRepositoryBuilder

/** Installs per-connection game handlers against that connection's bounded action queue. */
fun HandlerRepositoryBuilder.installGameHandlers(
    actions: PlayerActionSink,
    huffman: HuffmanCodec,
): HandlerRepositoryBuilder =
    bind(MoveGameClick::class.java, MoveGameClickHandler(actions))
        .bind(IfButtonX::class.java, IfButtonXHandler(actions))
        .bind(ClientCheat::class.java, ClientCheatHandler(actions))
        .bind(MessagePublic::class.java, MessagePublicHandler(huffman, actions))
        .bind(SetChatFilterSettings::class.java, SetChatFilterSettingsHandler(actions))
