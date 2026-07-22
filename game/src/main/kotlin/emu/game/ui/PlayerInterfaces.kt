package emu.game.ui

/** Bounded interface-tree state and ordered client UI operations for one player. */
class PlayerInterfaces {
    private val subInterfaces = linkedMapOf<Component, OpenSubInterface>()
    private val visibleSubInterfaces = mutableMapOf<Int, Int>()
    private val closeTriggers = ArrayDeque<Int>()
    private val clientUpdates = ArrayDeque<PlayerInterfaceUpdate>()
    private var topLevel: Int? = null
    private var modalCount = 0
    private var clientSynchronized = false

    /** Replaces the top-level frame and clears interfaces attached to the old frame. */
    fun openTopLevel(interfaceId: Int) {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        topLevel = interfaceId
        subInterfaces.clear()
        visibleSubInterfaces.clear()
        modalCount = 0
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

    /** Whether a protected modal currently blocks ordinary player work. */
    fun hasModal(): Boolean = modalCount != 0

    /** Queues one named cache client script after the base interface tree is synchronized. */
    fun runClientScript(script: ClientScript, vararg arguments: Any) {
        check(clientSynchronized) { "client scripts require a synchronized interface tree" }
        require(arguments.all { it is Int || it is String }) {
            "client script arguments must be Int or String"
        }
        ensureClientUpdateCapacity()
        clientUpdates.addLast(PlayerInterfaceUpdate.RunClientScript(script, arguments.toList()))
    }

    /** Closes all protected modal subinterface trees. */
    fun closeModal(): Boolean {
        if (modalCount == 0) return false
        val destinations = modalCloseDestinations()
        ensureCloseTriggerCapacity(destinations.size)
        if (clientSynchronized) ensureClientUpdateCapacity(destinations.size)
        for (destination in destinations) {
            val subInterface = checkNotNull(subInterfaces[destination])
            removeAt(destination)
            closeTriggers.addLast(subInterface.interfaceId)
            if (clientSynchronized) {
                clientUpdates.addLast(PlayerInterfaceUpdate.CloseSubInterface(destination))
            }
        }
        return destinations.isNotEmpty()
    }

    /** Removes the next interface id whose `IF_CLOSE` content still needs to run. */
    fun pollCloseTrigger(): Int? = closeTriggers.removeFirstOrNull()

    /** Marks the current base tree as published without replaying its construction. */
    fun markClientSynchronized() {
        clientUpdates.clear()
        clientSynchronized = true
    }

    /** Removes and returns ordered interface-tree changes awaiting client publication. */
    fun drainClientUpdates(): List<PlayerInterfaceUpdate> {
        if (clientUpdates.isEmpty()) return emptyList()
        return buildList(clientUpdates.size) {
            while (clientUpdates.isNotEmpty()) add(clientUpdates.removeFirst())
        }
    }

    private fun openSubInterface(destination: Component, interfaceId: Int, modal: Boolean) {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        require(isVisible(destination)) { "subinterface destination is not visible: $destination" }
        if (destination !in subInterfaces) {
            check(subInterfaces.size < MAX_OPEN_SUB_INTERFACES) {
                "open subinterface capacity exceeded"
            }
        }
        if (clientSynchronized) ensureClientUpdateCapacity()
        removeAt(destination)
        subInterfaces[destination] = OpenSubInterface(interfaceId, modal)
        if (modal) modalCount++
        visibleSubInterfaces.merge(interfaceId, 1, Int::plus)
        if (clientSynchronized) {
            clientUpdates.addLast(
                PlayerInterfaceUpdate.OpenSubInterface(destination, interfaceId, modal),
            )
        }
    }

    private fun removeAt(destination: Component) {
        val removed = subInterfaces.remove(destination) ?: return
        if (removed.modal) modalCount--
        val remaining = checkNotNull(visibleSubInterfaces[removed.interfaceId]) - 1
        if (remaining == 0) visibleSubInterfaces.remove(removed.interfaceId)
        else visibleSubInterfaces[removed.interfaceId] = remaining
        val children = subInterfaces.keys.filter { it.interfaceId == removed.interfaceId }
        children.forEach(::removeAt)
    }

    private fun modalCloseDestinations(): List<Component> {
        val remaining = LinkedHashSet(subInterfaces.keys)
        val destinations = ArrayList<Component>()
        for ((destination, subInterface) in subInterfaces) {
            if (!subInterface.modal || destination !in remaining) continue
            destinations += destination
            removeTreeFrom(destination, remaining)
        }
        return destinations
    }

    private fun removeTreeFrom(destination: Component, remaining: MutableSet<Component>) {
        if (!remaining.remove(destination)) return
        val interfaceId = checkNotNull(subInterfaces[destination]).interfaceId
        val children = remaining.filter { it.interfaceId == interfaceId }
        children.forEach { removeTreeFrom(it, remaining) }
    }

    private fun ensureClientUpdateCapacity(additional: Int = 1) {
        require(additional >= 0) { "additional client update count must be non-negative" }
        check(additional <= MAX_PENDING_CLIENT_UPDATES - clientUpdates.size) {
            "pending client interface update capacity exceeded"
        }
    }

    private fun ensureCloseTriggerCapacity(additional: Int) {
        require(additional >= 0) { "additional close trigger count must be non-negative" }
        check(additional <= MAX_PENDING_CLOSE_TRIGGERS - closeTriggers.size) {
            "pending interface close trigger capacity exceeded"
        }
    }

    private data class OpenSubInterface(val interfaceId: Int, val modal: Boolean)

    private companion object {
        const val MAX_OPEN_SUB_INTERFACES = 128
        const val MAX_PENDING_CLOSE_TRIGGERS = MAX_OPEN_SUB_INTERFACES
        const val MAX_PENDING_CLIENT_UPDATES = 256
        val UNSIGNED_SHORT = 0..0xFFFF
    }
}
