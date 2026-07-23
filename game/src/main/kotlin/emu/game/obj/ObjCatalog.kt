package emu.game.obj

/** Immutable object-type lookup used by authoritative game content. */
fun interface ObjCatalog {
    operator fun get(type: Int): ObjType?

    /** All cache objects with the requested display name, preserving type-id order. */
    fun findByName(name: String): List<ObjType> =
        buildList {
            for (id in 0..MAX_TYPE) {
                val type = get(id) ?: continue
                if (type.name.equals(name, ignoreCase = true)) add(type)
            }
        }

    companion object {
        val EMPTY = ObjCatalog { null }

        private const val MAX_TYPE = 0xFFFF
    }
}
