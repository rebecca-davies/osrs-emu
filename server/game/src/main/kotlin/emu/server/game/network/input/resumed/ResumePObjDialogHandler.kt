package emu.server.game.network.input.resumed

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.script.input.ObjDialogInput
import emu.protocol.osrs239.game.message.resumed.ResumePObjDialog
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Stages an object-dialog response for ordered world-thread script resumption. */
class ResumePObjDialogHandler(private val actions: PlayerActionSink) : MessageHandler<ResumePObjDialog> {
    override suspend fun handle(message: ResumePObjDialog, ctx: HandlerContext) {
        val action = PlayerAction.ResumeObjDialog(ObjDialogInput(message.obj))
        if (!actions.submit(action)) throw IncomingPlayerActionQueueOverflow
    }
}
