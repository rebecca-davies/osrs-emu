package emu.server.game.world.obj

import emu.cache.def.ItemDefinition
import emu.game.obj.ObjCatalog
import emu.game.obj.ObjType
import emu.game.obj.Wearpos

/** Maps cache item definitions into the small immutable object model owned by game. */
class CacheObjCatalog(items: Iterable<ItemDefinition>) : ObjCatalog {
    private val types = arrayOfNulls<ObjType>(MAX_OBJ_TYPE + 1)

    init {
        for (definition in items) {
            val name = definition.name?.takeIf(String::isNotBlank) ?: continue
            if (definition.id !in types.indices) continue
            types[definition.id] = definition.toObjType(name)
        }
    }

    override fun get(type: Int): ObjType? = types.getOrNull(type)

    private fun ItemDefinition.toObjType(name: String): ObjType =
        ObjType(
            id = id,
            name = name,
            stackable = stackable == STACKABLE,
            wearpos = readWearpos("wearPos1", wearPos1),
            wearpos2 = readWearpos("wearPos2", wearPos2),
            wearpos3 = readWearpos("wearPos3", wearPos3),
        )

    private fun ItemDefinition.readWearpos(field: String, slot: Int?): Wearpos? {
        if (slot == null || slot == NULL_WEARPOS) return null
        return requireNotNull(Wearpos.fromSlot(slot)) {
            "item $id has invalid $field $slot"
        }
    }

    private companion object {
        const val MAX_OBJ_TYPE = 0xFFFF
        const val NULL_WEARPOS = -1
        const val STACKABLE = 1
    }
}
