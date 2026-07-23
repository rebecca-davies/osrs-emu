package emu.server.game.network.input

import emu.compression.HuffmanCodec
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.message.chat.MessagePublic
import emu.protocol.osrs239.game.message.chat.SetChatFilterSettings
import emu.protocol.osrs239.game.message.client.ClientCheat
import emu.protocol.osrs239.game.message.client.Idle
import emu.protocol.osrs239.game.message.component.CloseModal
import emu.protocol.osrs239.game.message.component.IfButtonX
import emu.protocol.osrs239.game.message.loc.OpLoc1
import emu.protocol.osrs239.game.message.movement.MoveGameClick
import emu.protocol.osrs239.game.message.npc.OpNpc2
import emu.protocol.osrs239.game.message.resumed.ResumePCountDialog
import emu.protocol.osrs239.game.message.resumed.ResumePObjDialog
import emu.server.game.network.input.chat.MessagePublicHandler
import emu.server.game.network.input.chat.SetChatFilterSettingsHandler
import emu.server.game.network.input.client.IdleHandler
import emu.server.game.network.input.command.ClientCommandHandler
import emu.server.game.network.input.loc.OpLoc1Handler
import emu.server.game.network.input.movement.MoveGameClickHandler
import emu.server.game.network.input.npc.OpNpc2Handler
import emu.server.game.network.input.resumed.ResumePCountDialogHandler
import emu.server.game.network.input.resumed.ResumePObjDialogHandler
import emu.server.game.network.input.ui.CloseModalHandler
import emu.server.game.network.input.ui.IfButtonXHandler
import emu.transport.pipeline.handler.HandlerRepositoryBuilder

/** Installs per-connection game handlers against that connection's bounded action queue. */
fun HandlerRepositoryBuilder.installGameHandlers(
    actions: PlayerActionSink,
    huffman: HuffmanCodec,
): HandlerRepositoryBuilder =
    bind(MoveGameClick::class.java, MoveGameClickHandler(actions))
        .bind(IfButtonX::class.java, IfButtonXHandler(actions))
        .bind(ClientCheat::class.java, ClientCommandHandler(actions))
        .bind(CloseModal::class.java, CloseModalHandler(actions))
        .bind(Idle::class.java, IdleHandler(actions))
        .bind(MessagePublic::class.java, MessagePublicHandler(huffman, actions))
        .bind(SetChatFilterSettings::class.java, SetChatFilterSettingsHandler(actions))
        .bind(OpLoc1::class.java, OpLoc1Handler(actions))
        .bind(OpNpc2::class.java, OpNpc2Handler(actions))
        .bind(ResumePCountDialog::class.java, ResumePCountDialogHandler(actions))
        .bind(ResumePObjDialog::class.java, ResumePObjDialogHandler(actions))
