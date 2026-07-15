package emu.game.ui

/** Open top-level, overlay, and modal interfaces for one player. */
class PlayerInterfaces {
    private val subInterfaces = linkedMapOf<Component, OpenSubInterface>()
    private val visibleSubInterfaces = mutableMapOf<Int, Int>()
    private val closeTriggers = ArrayDeque<Int>()
    private val clientUpdates = ArrayDeque<PlayerInterfaceUpdate>()
    private var topLevel: Int? = null
    private var clientSynchronized = false

    /** Replaces the top-level frame and clears interfaces attached to the old frame. */
    fun openTopLevel(interfaceId: Int) {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        topLevel = interfaceId
        subInterfaces.clear()
        visibleSubInterfaces.clear()
        closeTriggers.clear()
        clientUpdates.clear()
        if (clientSynchronized) {
            clientUpdates.addLast(PlayerInterfaceUpdate.OpenTopLevel(interfaceId))
        }
    }

    /** Marks a non-blocking overlay interface as attached to the current frame. */
    fun openOverlay(destination: Component, interfaceId: Int) =
        openSubInterface(destination, interfaceId, modal = false)

    /** Marks a protected modal interface as attached to the current frame. */
    fun openModal(destination: Component, interfaceId: Int) =
        openSubInterface(destination, interfaceId, modal = true)

    /** Interface currently attached at [destination], or null when that component is empty. */
    fun subInterfaceAt(destination: Component): Int? = subInterfaces[destination]?.interfaceId

    /** Whether the component belongs to the currently published interface tree. */
    fun isVisible(component: Component): Boolean =
        component.interfaceId == topLevel || visibleSubInterfaces.containsKey(component.interfaceId)

    /** Closes all protected modal subinterface trees. */
    fun closeModal(): Boolean {
        val destinations =
            subInterfaces.entries
                .filter { it.value.modal }
                .map(Map.Entry<Component, OpenSubInterface>::key)
        for (destination in destinations) {
            val subInterface = subInterfaces[destination]
            if (subInterface?.modal != true) continue
            removeAt(destination)
            closeTriggers.addLast(subInterface.interfaceId)
            if (clientSynchronized) {
                clientUpdates.addLast(PlayerInterfaceUpdate.CloseSubInterface(destination))
            }
        }
        return destinations.isNotEmpty()
    }

    /** Removes and returns interface ids whose `IF_CLOSE` content still needs to run. */
    fun drainCloseTriggers(): List<Int> = buildList {
        while (closeTriggers.isNotEmpty()) add(closeTriggers.removeFirst())
    }

    /** Marks the current base tree as published without replaying its construction. */
    fun markClientSynchronized() {
        clientUpdates.clear()
        clientSynchronized = true
    }

    /** Removes and returns ordered interface-tree changes awaiting client publication. */
    fun drainClientUpdates(): List<PlayerInterfaceUpdate> = buildList {
        while (clientUpdates.isNotEmpty()) add(clientUpdates.removeFirst())
    }

    private fun openSubInterface(destination: Component, interfaceId: Int, modal: Boolean) {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        require(isVisible(destination)) { "subinterface destination is not visible: $destination" }
        removeAt(destination)
        subInterfaces[destination] = OpenSubInterface(interfaceId, modal)
        visibleSubInterfaces.merge(interfaceId, 1, Int::plus)
        if (clientSynchronized) {
            clientUpdates.addLast(
                PlayerInterfaceUpdate.OpenSubInterface(destination, interfaceId, modal),
            )
        }
    }

    private fun removeAt(destination: Component) {
        val removed = subInterfaces.remove(destination) ?: return
        val remaining = checkNotNull(visibleSubInterfaces[removed.interfaceId]) - 1
        if (remaining == 0) visibleSubInterfaces.remove(removed.interfaceId)
        else visibleSubInterfaces[removed.interfaceId] = remaining
        val children = subInterfaces.keys.filter { it.interfaceId == removed.interfaceId }
        children.forEach(::removeAt)
    }

    private data class OpenSubInterface(val interfaceId: Int, val modal: Boolean)

    private companion object {
        val UNSIGNED_SHORT = 0..0xFFFF
    }
}
