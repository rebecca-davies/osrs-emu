package emu.server.game.network.output.ui

import emu.game.content.ui.gameframe.Gameframe
import emu.game.content.ui.gameframe.GameframeInventory
import emu.game.content.ui.gameframe.GameframeInventorySource
import emu.game.obj.Obj
import emu.game.player.Player
import emu.game.ui.PlayerInterfaceUpdate
import emu.protocol.osrs239.game.message.audio.AmbienceStop
import emu.protocol.osrs239.game.message.camera.CamReset
import emu.protocol.osrs239.game.message.component.IfCloseSub
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.message.component.IfOpenSub.Companion.MODAL
import emu.protocol.osrs239.game.message.component.IfOpenSub.Companion.OVERLAY
import emu.protocol.osrs239.game.message.component.IfOpenTop
import emu.protocol.osrs239.game.message.component.IfResync
import emu.protocol.osrs239.game.message.client.RunClientScript
import emu.protocol.osrs239.game.message.inventory.UpdateInvFull
import emu.transport.message.OutgoingMessage

/** Converts the configured initial gameframe into revision-239 interface messages. */
internal class PlayerInterfaceOutput(private val gameframe: Gameframe) {
    private val inventoriesBySource =
        gameframe.initialInventories
            .mapNotNull { inventory -> inventory.source?.let { it to inventory } }
            .toMap()

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

    fun initialInventories(player: Player): List<UpdateInvFull> =
        gameframe.initialInventories.map { inventory ->
            inventory.message(
                when (inventory.source) {
                    GameframeInventorySource.INVENTORY -> player.inventory.loginSync()
                    GameframeInventorySource.WORN -> player.worn.loginSync()
                    null -> emptyList()
                },
            )
        }

    /** Replaces the client inventory configured for one authoritative player container. */
    fun inventoryUpdate(source: GameframeInventorySource, objects: List<Obj?>): UpdateInvFull =
        requireNotNull(inventoriesBySource[source]) {
            "gameframe inventory source is not configured: ${source.configName}"
        }.message(objects)

    private fun GameframeInventory.message(objects: List<Obj?>): UpdateInvFull =
        UpdateInvFull(
            ROOT_INTERFACE,
            componentId,
            inventoryId,
            objects.map { obj ->
                if (obj == null) UpdateInvFull.Obj(EMPTY_OBJ, 0)
                else UpdateInvFull.Obj(obj.type, obj.count)
            },
        )

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
                is PlayerInterfaceUpdate.RunClientScript ->
                    RunClientScript(update.script.id, update.arguments)
            }

        private const val ROOT_INTERFACE = -1
        private const val EMPTY_OBJ = -1
    }
}
