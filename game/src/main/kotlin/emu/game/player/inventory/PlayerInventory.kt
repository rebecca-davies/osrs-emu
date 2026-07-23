package emu.game.player.inventory

import emu.game.obj.Obj
import emu.game.obj.ObjStack
import emu.game.obj.ObjType

/** Fixed-capacity backpack with sparse full-container client synchronization. */
class PlayerInventory {
    private val slots = arrayOfNulls<Obj>(CAPACITY)
    private var clientUpdatePending = false

    /** Adds a stack or individual objects atomically when the backpack has enough space. */
    fun add(type: ObjType, count: Int = 1): Boolean {
        require(count > 0) { "object count must be positive" }
        if (type.stackable) {
            val existing = slots.indexOfFirst { it?.type == type.id }
            if (existing != -1) {
                val obj = checkNotNull(slots[existing])
                if (obj.count > Int.MAX_VALUE - count) return false
                slots[existing] = obj.copy(count = obj.count + count)
                clientUpdatePending = true
                return true
            }
        } else {
            if (slots.count { it == null } < count) return false
            var remaining = count
            for (slot in slots.indices) {
                if (remaining == 0) break
                if (slots[slot] != null) continue
                slots[slot] = Obj(type.id)
                remaining--
            }
            clientUpdatePending = true
            return true
        }
        val empty = slots.indexOfFirst { it == null }
        if (empty == -1) return false
        slots[empty] = Obj(type.id, count)
        clientUpdatePending = true
        return true
    }

    /** Prepares a complete replacement after adding [objects], or null without mutating on overflow. */
    internal fun replacementAdding(objects: List<ObjStack>): List<Obj?>? {
        val replacement = slots.toMutableList()
        for (stack in objects) {
            if (!replacement.add(stack)) return null
        }
        return replacement
    }

    /** Prepares an empty backpack populated by [objects], or null without mutating on overflow. */
    internal fun replacementWith(objects: List<ObjStack>): List<Obj?>? {
        val replacement = MutableList<Obj?>(CAPACITY) { null }
        for (stack in objects) {
            if (!replacement.add(stack)) return null
        }
        return replacement
    }

    /** Atomically installs one already preflighted complete slot replacement. */
    internal fun replaceWith(replacement: List<Obj?>) {
        require(replacement.size == CAPACITY) { "inventory replacement must contain $CAPACITY slots" }
        replacement.toTypedArray().copyInto(slots)
        clientUpdatePending = true
    }

    /** Complete slot state in client order. */
    fun loginSync(): List<Obj?> = slots.toList()

    /** Changed slot state, or null when no client update is pending. */
    fun drainClientUpdate(): List<Obj?>? {
        if (!clientUpdatePending) return null
        clientUpdatePending = false
        return slots.toList()
    }

    private fun MutableList<Obj?>.add(stack: ObjStack): Boolean {
        val type = stack.type
        if (type.stackable) {
            val existing = indexOfFirst { it?.type == type.id }
            if (existing != -1) {
                val obj = checkNotNull(this[existing])
                if (obj.count > Int.MAX_VALUE - stack.count) return false
                this[existing] = obj.copy(count = obj.count + stack.count)
                return true
            }
        } else {
            if (stack.count > count { it == null }) return false
            var remaining = stack.count
            for (slot in indices) {
                if (remaining == 0) break
                if (this[slot] != null) continue
                this[slot] = Obj(type.id)
                remaining--
            }
            return true
        }
        val empty = indexOfFirst { it == null }
        if (empty == -1) return false
        this[empty] = stack.toObj()
        return true
    }

    companion object {
        const val CAPACITY = 28
    }
}
