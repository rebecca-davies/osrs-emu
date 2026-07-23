package emu.game.obj

/** Immutable lookup of Jagex named-object enum values used by server-authoritative content. */
fun interface NamedObjEnumCatalog {
    operator fun get(enumId: Int): List<Int>?

    companion object {
        val EMPTY = NamedObjEnumCatalog { null }
    }
}
