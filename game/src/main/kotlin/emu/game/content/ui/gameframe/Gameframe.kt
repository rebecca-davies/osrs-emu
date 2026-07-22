package emu.game.content.ui.gameframe

import emu.game.ui.PlayerInterfaces

/** Immutable initial top-level interface tree shared by game state and protocol output. */
data class Gameframe(
    val topLevelInterface: Int,
    val subInterfaces: List<GameframeSubInterface>,
    val initialInventories: List<GameframeInventory> = emptyList(),
) {
    init {
        require(topLevelInterface in 0..0xFFFF) { "top-level interface must fit an unsigned short" }
        require(subInterfaces.distinctBy { it.destination }.size == subInterfaces.size) {
            "gameframe destinations must be unique"
        }
        val sources = initialInventories.mapNotNull(GameframeInventory::source)
        require(sources.distinct().size == sources.size) {
            "gameframe inventory sources must be unique"
        }
        validateOrderedTree()
    }

    /** Replaces [interfaces] with this initial gameframe tree. */
    fun open(interfaces: PlayerInterfaces) {
        interfaces.openTopLevel(topLevelInterface)
        for (sub in subInterfaces) {
            if (sub.modal) interfaces.openModal(sub.destination, sub.interfaceId)
            else interfaces.openOverlay(sub.destination, sub.interfaceId)
        }
    }

    private fun validateOrderedTree() {
        val visibleInterfaces = mutableSetOf(topLevelInterface)
        for ((index, sub) in subInterfaces.withIndex()) {
            require(sub.destination.interfaceId in visibleInterfaces) {
                "gameframe subinterface $index targets an interface that is not visible yet: " +
                    sub.destination
            }
            visibleInterfaces += sub.interfaceId
        }
    }
}
