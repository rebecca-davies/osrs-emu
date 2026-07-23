package emu.game.ui

/** Cache interface attachment and component visibility applied on its first open. */
class InterfaceLayout(
    val destination: Component,
    val interfaceId: Int,
    visibleComponents: List<Component> = emptyList(),
    hiddenComponents: List<Component> = emptyList(),
) {
    internal val visibleComponents = visibleComponents.toList()
    internal val hiddenComponents = hiddenComponents.toList()

    init {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        val configuredComponents = this.visibleComponents + this.hiddenComponents
        require(configuredComponents.all { it.interfaceId == interfaceId }) {
            "layout components must belong to interface $interfaceId"
        }
        require(configuredComponents.distinct().size == configuredComponents.size) {
            "layout components must be unique"
        }
    }

    internal val componentUpdateCount: Int
        get() = visibleComponents.size + hiddenComponents.size

    private companion object {
        val UNSIGNED_SHORT = 0..0xFFFF
    }
}
