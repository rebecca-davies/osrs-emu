package emu.game.ui

/** A non-suspending game-cycle action retaining the complete click for slots and alternate ops. */
fun interface ButtonAction {
    fun handle(click: ButtonClick)
}

/**
 * Immutable component-to-action table. Each action receives the click's operation, slot, and
 * object context. Unknown components are rejected.
 */
class ButtonActionRegistry internal constructor(
    private val actions: Map<Int, ButtonAction>,
) {
    fun dispatch(click: ButtonClick): Boolean {
        val action = actions[click.packedComponent] ?: return false
        action.handle(click)
        return true
    }
}

/** Builds an immutable button-action registry. */
fun buttonActions(init: ButtonActionRegistryBuilder.() -> Unit): ButtonActionRegistry =
    ButtonActionRegistryBuilder().apply(init).build()

/** Builds a button registry while rejecting duplicate component bindings. */
class ButtonActionRegistryBuilder {
    private val actions = LinkedHashMap<Int, ButtonAction>()

    fun onButton(interfaceId: Int, componentId: Int, action: ButtonAction) {
        val packed = ButtonClick(interfaceId, componentId, -1, -1, 1).packedComponent
        require(actions.putIfAbsent(packed, action) == null) {
            "button action already registered for $interfaceId:$componentId"
        }
    }

    internal fun build(): ButtonActionRegistry = ButtonActionRegistry(actions.toMap())
}
