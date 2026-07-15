package emu.game.ui

/**
 * Revision-neutral interface interaction admitted by the network edge.
 *
 * [sub] is the component's inventory slot and [obj] is its transmitted object id when applicable;
 * both are `-1` for ordinary buttons. [op] is the one-based client operation number.
 */
data class ButtonClick(
    val interfaceId: Int,
    val componentId: Int,
    val sub: Int,
    val obj: Int,
    val op: Int,
) {
    init {
        require(interfaceId >= 0) { "interface id must be non-negative" }
        require(componentId in 0..0xFFFF) { "component id must fit an unsigned short" }
        require(op in 1..MAX_BUTTON_OP) { "button operation must be in 1..$MAX_BUTTON_OP" }
    }

    val packedComponent: Int
        get() = (interfaceId shl 16) or componentId

    private companion object {
        const val MAX_BUTTON_OP = 10
    }
}
