package emu.game.ui

/** One authoritative interface-tree change awaiting client synchronization. */
sealed interface PlayerInterfaceUpdate {
    data class OpenTopLevel(val interfaceId: Int) : PlayerInterfaceUpdate

    data class OpenSubInterface(
        val destination: Component,
        val interfaceId: Int,
        val modal: Boolean,
    ) : PlayerInterfaceUpdate

    data class CloseSubInterface(val destination: Component) : PlayerInterfaceUpdate
}
