package emu.game.player.inventory

import emu.game.obj.Obj
import emu.game.obj.ObjStack
import emu.game.obj.ObjType
import emu.game.obj.Wearpos

/** Fixed worn inventory that preserves appearance and multi-slot equipment invariants. */
class PlayerWorn {
    private val slots = arrayOfNulls<WornObj>(Wearpos.entries.size)
    private var clientUpdatePending = false

    var revision: Int = 0
        private set

    /** Equips an object and returns objects displaced from every occupied server slot. */
    fun equip(type: ObjType, count: Int = 1): List<Obj> {
        require(count == 1 || type.stackable) { "only stackable worn objects may have a quantity" }
        val primary = requireNotNull(type.wearpos) { "object ${type.id} is not wearable" }
        val occupied = type.occupiedWearpos()
        val displaced = mutableListOf<Obj>()
        for (wearpos in Wearpos.entries) {
            val worn = slots[wearpos.slot] ?: continue
            if (occupied.any(worn::occupies)) remove(wearpos, displaced)
        }
        slots[primary.slot] = WornObj(Obj(type.id, count), type)
        clientUpdatePending = true
        revision++
        return displaced
    }

    /** Prepares equipping [type], including typed objects displaced from occupied server slots. */
    internal fun replacementEquipping(type: ObjType, count: Int = 1): WornReplacement? {
        if (count != 1 && !type.stackable) return null
        val primary = type.wearpos ?: return null
        val occupied = type.occupiedWearpos()
        val replacement = slots.toMutableList()
        val displaced = ArrayList<ObjStack>()
        for (wearpos in Wearpos.entries) {
            val worn = replacement[wearpos.slot] ?: continue
            if (occupied.any(worn::occupies)) {
                replacement[wearpos.slot] = null
                displaced += ObjStack(worn.type, worn.obj.count)
            }
        }
        replacement[primary.slot] = WornObj(Obj(type.id, count), type)
        return WornReplacement(replacement, displaced)
    }

    /** Prepares a complete worn replacement, rejecting overlaps without changing live state. */
    internal fun replacementWith(objects: List<ObjStack>): List<WornObj?>? {
        if (objects.size > Wearpos.entries.size) return null
        val replacement = MutableList<WornObj?>(Wearpos.entries.size) { null }
        val occupied = BooleanArray(Wearpos.entries.size)
        for (stack in objects) {
            val type = stack.type
            val primary = type.wearpos ?: return null
            if (stack.count != 1 && !type.stackable) return null
            val positions = type.occupiedWearpos()
            if (positions.any { occupied[it.slot] }) return null
            positions.forEach { occupied[it.slot] = true }
            replacement[primary.slot] = WornObj(stack.toObj(), type)
        }
        return replacement
    }

    /** Atomically installs one already preflighted complete worn replacement. */
    internal fun replaceWith(replacement: List<WornObj?>) {
        require(replacement.size == Wearpos.entries.size) {
            "worn replacement must contain ${Wearpos.entries.size} slots"
        }
        replacement.toTypedArray().copyInto(slots)
        clientUpdatePending = true
        revision++
    }

    operator fun get(wearpos: Wearpos): WornObj? = slots[wearpos.slot]

    /** Complete worn slot state in client order. */
    fun loginSync(): List<Obj?> = slots.map { it?.obj }

    /** Changed worn slot state, or null when no client update is pending. */
    fun drainClientUpdate(): List<Obj?>? {
        if (!clientUpdatePending) return null
        clientUpdatePending = false
        return slots.map { it?.obj }
    }

    private fun remove(wearpos: Wearpos, displaced: MutableList<Obj>) {
        val removed = slots[wearpos.slot] ?: return
        slots[wearpos.slot] = null
        displaced += removed.obj
    }

    private fun ObjType.occupiedWearpos(): List<Wearpos> =
        buildList {
            add(requireNotNull(wearpos))
            wearpos2?.takeUnless(Wearpos::clientOnly)?.let(::add)
            wearpos3?.takeUnless(Wearpos::clientOnly)?.let(::add)
        }

    /** One worn object paired with the definition that governs its occupied appearance slots. */
    data class WornObj(val obj: Obj, val type: ObjType) {
        /** Whether this object definition occupies [wearpos] directly or hides it client-side. */
        fun occupies(wearpos: Wearpos): Boolean =
            type.wearpos == wearpos ||
                !wearpos.clientOnly && (type.wearpos2 == wearpos || type.wearpos3 == wearpos)
    }

    /** Immutable result of a non-mutating worn-equipment preflight. */
    internal data class WornReplacement(
        val slots: List<WornObj?>,
        val displaced: List<ObjStack>,
    )
}
