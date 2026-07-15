package emu.game.ui

/** A non-suspending game-cycle action retaining the complete click for slots and alternate ops. */
fun interface ButtonAction {
    fun handle(click: ButtonClick)
}

/**
 * Immutable component-to-content dispatch table, modelled after RuneScript/rsmod button triggers.
 *
 * One action is registered per packed interface component and receives every operation for that
 * component. This avoids a growing protocol-handler `when` while preserving the `op`, slot, and
 * object context needed by inventory and modal content later. Unknown components are rejected.
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

/** Declarative content-side button registration entry point. */
fun buttonActions(init: ButtonActionRegistryBuilder.() -> Unit): ButtonActionRegistry =
    ButtonActionRegistryBuilder().apply(init).build()

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
