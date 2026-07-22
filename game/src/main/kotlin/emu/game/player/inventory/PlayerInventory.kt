package emu.game.player.inventory

import emu.game.obj.Obj
import emu.game.obj.ObjType

/** Fixed-capacity backpack with sparse full-container client synchronization. */
class PlayerInventory {
    private val slots = arrayOfNulls<Obj>(CAPACITY)
    private var clientUpdatePending = false

    /** Adds one object stack when the backpack has enough compatible space. */
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
        } else if (count != 1) {
            return false
        }
        val empty = slots.indexOfFirst { it == null }
        if (empty == -1) return false
        slots[empty] = Obj(type.id, count)
        clientUpdatePending = true
        return true
    }

    /** Complete slot state in client order. */
    fun loginSync(): List<Obj?> = slots.toList()

    /** Changed slot state, or null when no client update is pending. */
    fun drainClientUpdate(): List<Obj?>? {
        if (!clientUpdatePending) return null
        clientUpdatePending = false
        return slots.toList()
    }

    companion object {
        const val CAPACITY = 28
    }
}
