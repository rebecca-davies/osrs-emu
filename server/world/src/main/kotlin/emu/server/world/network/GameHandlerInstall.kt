package emu.server.world.network

import emu.compression.HuffmanCodec
import emu.game.input.PlayerInputSink
import emu.server.world.network.handler.IfButtonXHandler
import emu.server.world.network.handler.MoveGameClickHandler
import emu.server.world.network.handler.MessagePublicHandler
import emu.server.world.network.handler.SetChatFilterSettingsHandler
import emu.transport.pipeline.HandlerRepositoryBuilder
import emu.protocol.osrs239.game.message.MoveGameClick
import emu.protocol.osrs239.game.message.IfButtonX
import emu.protocol.osrs239.game.message.MessagePublic
import emu.protocol.osrs239.game.message.SetChatFilterSettings

/** Binds per-connection game handlers whose mutable mailbox dependency cannot be a singleton. */
fun HandlerRepositoryBuilder.installGameHandlers(
    inputs: PlayerInputSink,
    huffman: HuffmanCodec,
): HandlerRepositoryBuilder =
    bind(MoveGameClick::class.java, MoveGameClickHandler(inputs))
        .bind(IfButtonX::class.java, IfButtonXHandler(inputs))
        .bind(MessagePublic::class.java, MessagePublicHandler(huffman, inputs))
        .bind(SetChatFilterSettings::class.java, SetChatFilterSettingsHandler(inputs))
