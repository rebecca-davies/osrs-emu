package emu.game.ui

import emu.game.obj.Obj

/** One ordered interface operation awaiting client synchronization. */
sealed interface PlayerInterfaceUpdate {
    data class OpenTopLevel(val interfaceId: Int) : PlayerInterfaceUpdate

    data class OpenSubInterface(
        val destination: Component,
        val interfaceId: Int,
        val modal: Boolean,
    ) : PlayerInterfaceUpdate

    data class CloseSubInterface(val destination: Component) : PlayerInterfaceUpdate

    data class RunClientScript(
        val script: ClientScript,
        val arguments: List<Any>,
    ) : PlayerInterfaceUpdate

    data class SetText(val component: Component, val text: String) : PlayerInterfaceUpdate

    data class SetHidden(val component: Component, val hidden: Boolean) : PlayerInterfaceUpdate

    data class TransmitInventory(
        val inventoryId: Int,
        val objects: List<Obj?>,
    ) : PlayerInterfaceUpdate
}
