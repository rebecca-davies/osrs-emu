package emu.game.ui

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
}
