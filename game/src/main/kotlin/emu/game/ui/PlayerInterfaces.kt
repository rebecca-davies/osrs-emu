package emu.game.ui

import emu.game.obj.Obj

/** Bounded interface-tree state and ordered client UI operations for one player. */
class PlayerInterfaces {
    private val subInterfaces = linkedMapOf<Component, OpenSubInterface>()
    private val visibleSubInterfaces = mutableMapOf<Int, Int>()
    private val replacedOverlays = mutableMapOf<Component, ReplacedOverlay>()
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
        replacedOverlays.clear()
        modalCount = 0
        closeTriggers.clear()
        clientUpdates.clear()
        if (clientSynchronized) {
            clientUpdates.addLast(PlayerInterfaceUpdate.OpenTopLevel(interfaceId))
        }
    }

    /** Marks a non-blocking overlay interface as attached to the current frame. */
    fun openOverlay(destination: Component, interfaceId: Int) {
        discardReplacementsInSubtree(destination)
        openSubInterface(destination, interfaceId, modal = false)
    }

    /** Marks a protected modal interface as attached to the current frame. */
    fun openModal(destination: Component, interfaceId: Int) {
        discardReplacementsInSubtree(destination)
        openSubInterface(destination, interfaceId, modal = true)
    }

    /** Opens and initializes [layout] as a modal, or returns false when it is already open. */
    fun openModal(layout: InterfaceLayout): Boolean {
        if (isOpen(layout)) return false
        ensureLayoutCapacity(layout)
        discardReplacementsInSubtree(layout.destination)
        openSubInterface(layout.destination, layout.interfaceId, modal = true)
        applyLayout(layout)
        return true
    }

    /** Replaces an overlay and retains its subtree, or returns false when [layout] is already open. */
    fun replaceOverlay(layout: InterfaceLayout): Boolean {
        if (subInterfaceAt(layout.destination) == layout.interfaceId) return false
        check(layout.destination !in replacedOverlays) {
            "overlay destination already has a retained replacement: ${layout.destination}"
        }
        ensureLayoutCapacity(layout)
        val retainedTree = subInterfaceTree(layout.destination)
        discardNestedReplacements(retainedTree)
        openSubInterface(layout.destination, layout.interfaceId, modal = false)
        replacedOverlays[layout.destination] = ReplacedOverlay(layout.interfaceId, retainedTree)
        applyLayout(layout)
        return true
    }

    /** Restores a retained subtree and returns whether [layout] still owned its destination. */
    fun restoreOverlay(layout: InterfaceLayout): Boolean {
        val replacement = replacedOverlays[layout.destination] ?: return false
        if (
            replacement.interfaceId != layout.interfaceId ||
            subInterfaceAt(layout.destination) != layout.interfaceId
        ) {
            replacedOverlays.remove(layout.destination)
            return false
        }
        val currentTree = subInterfaceTree(layout.destination)
        val resultingSize = subInterfaces.size - currentTree.size + replacement.retainedTree.size
        check(resultingSize <= MAX_OPEN_SUB_INTERFACES) {
            "restored subinterface tree exceeds capacity"
        }
        if (clientSynchronized) {
            ensureClientUpdateCapacity(maxOf(1, replacement.retainedTree.size))
        }
        discardNestedReplacements(currentTree)
        removeAt(layout.destination)
        replacedOverlays.remove(layout.destination)
        if (replacement.retainedTree.isEmpty()) {
            if (clientSynchronized) {
                clientUpdates.addLast(PlayerInterfaceUpdate.CloseSubInterface(layout.destination))
            }
        } else {
            replacement.retainedTree.forEach { retained ->
                openSubInterface(
                    retained.destination,
                    retained.interfaceId,
                    retained.modal,
                )
            }
        }
        return true
    }

    /** Whether [layout] is attached at its configured destination. */
    fun isOpen(layout: InterfaceLayout): Boolean =
        subInterfaceAt(layout.destination) == layout.interfaceId

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

    /** Queues bounded CP-1252 text for a component in the current interface tree. */
    fun setText(component: Component, text: String) {
        require(CP1252.newEncoder().canEncode(text)) { "interface text must be encodable as CP-1252" }
        require('\u0000' !in text) { "interface text cannot contain NUL" }
        require(text.toByteArray(CP1252).size <= MAX_COMPONENT_TEXT_BYTES) {
            "interface text cannot exceed $MAX_COMPONENT_TEXT_BYTES bytes"
        }
        queueComponentUpdate(component, PlayerInterfaceUpdate.SetText(component, text))
    }

    /** Queues component visibility for a component in the current interface tree. */
    fun setHidden(component: Component, hidden: Boolean) {
        queueComponentUpdate(component, PlayerInterfaceUpdate.SetHidden(component, hidden))
    }

    /** Publishes a bounded temporary client inventory used only to render interface previews. */
    fun transmitInventory(inventoryId: Int, objects: List<Obj?>) {
        check(clientSynchronized) { "inventory transmission requires a synchronized interface tree" }
        require(inventoryId in UNSIGNED_SHORT) { "inventory id must fit an unsigned short" }
        require(objects.size <= MAX_TRANSMITTED_INVENTORY_SIZE) {
            "temporary inventory exceeds $MAX_TRANSMITTED_INVENTORY_SIZE slots"
        }
        ensureClientUpdateCapacity()
        clientUpdates.addLast(PlayerInterfaceUpdate.TransmitInventory(inventoryId, objects.toList()))
    }

    /** Closes all protected modal subinterface trees. */
    fun closeModal(): Boolean {
        if (modalCount == 0) return false
        val destinations = modalCloseDestinations()
        ensureCloseTriggerCapacity(destinations.size)
        if (clientSynchronized) ensureClientUpdateCapacity(destinations.size)
        for (destination in destinations) {
            val subInterface = checkNotNull(subInterfaces[destination])
            discardReplacementsInSubtree(destination)
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

    private fun applyLayout(layout: InterfaceLayout) {
        layout.visibleComponents.forEach { setHidden(it, hidden = false) }
        layout.hiddenComponents.forEach { setHidden(it, hidden = true) }
    }

    private fun ensureLayoutCapacity(layout: InterfaceLayout) {
        check(clientSynchronized) { "interface layouts require a synchronized interface tree" }
        ensureClientUpdateCapacity(1 + layout.componentUpdateCount)
    }

    private fun subInterfaceTree(destination: Component): List<SubInterfaceAttachment> {
        if (destination !in subInterfaces) return emptyList()
        val remaining = LinkedHashSet(subInterfaces.keys)
        return buildList { collectSubInterfaceTree(destination, remaining, this) }
    }

    private fun collectSubInterfaceTree(
        destination: Component,
        remaining: MutableSet<Component>,
        attachments: MutableList<SubInterfaceAttachment>,
    ) {
        if (!remaining.remove(destination)) return
        val subInterface = subInterfaces[destination] ?: return
        attachments += SubInterfaceAttachment(destination, subInterface.interfaceId, subInterface.modal)
        val children = remaining.filter { it.interfaceId == subInterface.interfaceId }
        children.forEach { collectSubInterfaceTree(it, remaining, attachments) }
    }

    private fun discardReplacementsInSubtree(destination: Component) {
        val tree = subInterfaceTree(destination)
        replacedOverlays.remove(destination)
        discardNestedReplacements(tree)
    }

    private fun discardNestedReplacements(tree: List<SubInterfaceAttachment>) {
        if (tree.isEmpty()) return
        val interfaceIds = tree.mapTo(mutableSetOf(), SubInterfaceAttachment::interfaceId)
        replacedOverlays.keys.removeAll { it.interfaceId in interfaceIds }
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

    private fun queueComponentUpdate(component: Component, update: PlayerInterfaceUpdate) {
        require(clientSynchronized) { "component updates require a synchronized interface tree" }
        require(isVisible(component)) { "component is not visible: $component" }
        ensureClientUpdateCapacity()
        clientUpdates.addLast(update)
    }

    private data class OpenSubInterface(val interfaceId: Int, val modal: Boolean)

    private data class ReplacedOverlay(
        val interfaceId: Int,
        val retainedTree: List<SubInterfaceAttachment>,
    )

    private data class SubInterfaceAttachment(
        val destination: Component,
        val interfaceId: Int,
        val modal: Boolean,
    )

    private companion object {
        const val MAX_OPEN_SUB_INTERFACES = 128
        const val MAX_PENDING_CLOSE_TRIGGERS = MAX_OPEN_SUB_INTERFACES
        const val MAX_PENDING_CLIENT_UPDATES = 256
        const val MAX_COMPONENT_TEXT_BYTES = 1_024
        const val MAX_TRANSMITTED_INVENTORY_SIZE = 256
        val UNSIGNED_SHORT = 0..0xFFFF
        val CP1252 = charset("windows-1252")
    }
}
