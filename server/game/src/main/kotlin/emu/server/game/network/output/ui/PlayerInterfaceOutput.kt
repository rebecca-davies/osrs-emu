package emu.server.game.network.output.ui

import emu.game.content.ui.gameframe.Gameframe
import emu.game.ui.PlayerInterfaceUpdate
import emu.protocol.osrs239.game.message.audio.AmbienceStop
import emu.protocol.osrs239.game.message.camera.CamReset
import emu.protocol.osrs239.game.message.component.IfCloseSub
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.message.component.IfOpenSub.Companion.MODAL
import emu.protocol.osrs239.game.message.component.IfOpenSub.Companion.OVERLAY
import emu.protocol.osrs239.game.message.component.IfOpenTop
import emu.protocol.osrs239.game.message.component.IfResync
import emu.protocol.osrs239.game.message.inventory.UpdateInvFull
import emu.transport.message.OutgoingMessage

/** Converts the configured initial gameframe into revision-239 interface messages. */
internal class PlayerInterfaceOutput(private val gameframe: Gameframe) {
    fun frameSubInterfaces(): List<IfOpenSub> =
        gameframe.subInterfaces.map { sub ->
            IfOpenSub(
                destinationInterfaceId = sub.destination.interfaceId,
                destinationComponentId = sub.destination.componentId,
                interfaceId = sub.interfaceId,
                type = if (sub.modal) MODAL else OVERLAY,
            )
        }

    fun frameMessages(): List<OutgoingMessage> {
        val frame = frameSubInterfaces()
        return buildList {
            add(IfOpenTop(gameframe.topLevelInterface))
            addAll(frame)
            add(CamReset)
            add(AmbienceStop(fade = true))
            add(IfResync(gameframe.topLevelInterface, frame))
        }
    }

    fun initialInventories(): List<UpdateInvFull> =
        gameframe.initialInventories.map { inventory ->
            UpdateInvFull(ROOT_INTERFACE, inventory.componentId, inventory.inventoryId)
        }

    companion object {
        fun message(update: PlayerInterfaceUpdate): OutgoingMessage =
            when (update) {
                is PlayerInterfaceUpdate.OpenTopLevel -> IfOpenTop(update.interfaceId)
                is PlayerInterfaceUpdate.OpenSubInterface ->
                    IfOpenSub(
                        destinationInterfaceId = update.destination.interfaceId,
                        destinationComponentId = update.destination.componentId,
                        interfaceId = update.interfaceId,
                        type = if (update.modal) MODAL else OVERLAY,
                    )
                is PlayerInterfaceUpdate.CloseSubInterface ->
                    IfCloseSub(
                        update.destination.interfaceId,
                        update.destination.componentId,
                    )
            }

        private const val ROOT_INTERFACE = -1
    }
}
