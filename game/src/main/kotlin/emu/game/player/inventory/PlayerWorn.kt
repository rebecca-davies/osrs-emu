package emu.game.player.inventory

import emu.game.obj.Obj
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
        val occupied =
            buildList {
                add(primary)
                type.wearpos2?.takeUnless(Wearpos::clientOnly)?.let(::add)
                type.wearpos3?.takeUnless(Wearpos::clientOnly)?.let(::add)
            }
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

    /** One worn object paired with the definition that governs its occupied appearance slots. */
    data class WornObj(val obj: Obj, val type: ObjType) {
        /** Whether this object definition occupies [wearpos] directly or hides it client-side. */
        fun occupies(wearpos: Wearpos): Boolean =
            type.wearpos == wearpos ||
                !wearpos.clientOnly && (type.wearpos2 == wearpos || type.wearpos3 == wearpos)
    }
}
