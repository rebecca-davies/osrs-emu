package emu.server.game.network.input.resumed

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.script.input.CountDialogInput
import emu.protocol.osrs239.game.message.resumed.ResumePCountDialog
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Stages a count-dialog response for ordered world-thread script resumption. */
class ResumePCountDialogHandler(private val actions: PlayerActionSink) :
    MessageHandler<ResumePCountDialog> {
    override suspend fun handle(message: ResumePCountDialog, ctx: HandlerContext) {
        val action = PlayerAction.ResumeCountDialog(CountDialogInput(message.count))
        if (!actions.submit(action)) throw IncomingPlayerActionQueueOverflow
    }
}
