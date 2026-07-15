package emu.game.ui

/**
 * Revision-neutral interface interaction queued by the network edge.
 *
 * [sub] is the component's inventory slot and [obj] is its transmitted object id when applicable;
 * both are `-1` for ordinary buttons. [op] is the one-based client operation number.
 */
data class ButtonClick(
    val interfaceId: Int,
    val componentId: Int,
    val sub: Int = -1,
    val obj: Int = -1,
    val op: Int = 1,
) {
    init {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        require(componentId in UNSIGNED_SHORT) { "component id must fit an unsigned short" }
        require(sub in OPTIONAL_UNSIGNED_SHORT) { "button sub must be -1 or fit the transmitted unsigned short" }
        require(obj in OPTIONAL_UNSIGNED_SHORT) { "button obj must be -1 or fit the transmitted unsigned short" }
        require(op in 1..MAX_BUTTON_OP) { "button operation must be in 1..$MAX_BUTTON_OP" }
    }

    val packedComponent: Int
        get() = (interfaceId shl 16) or componentId

    val component: Component
        get() = Component(packedComponent)

    val isComponentOnly: Boolean
        get() = sub == -1 && obj == -1

    val isPrimaryComponentClick: Boolean
        get() = isComponentOnly && op == 1

    private companion object {
        val UNSIGNED_SHORT = 0..0xFFFF
        val OPTIONAL_UNSIGNED_SHORT = -1..0xFFFE
        const val MAX_BUTTON_OP = 10
    }
}
