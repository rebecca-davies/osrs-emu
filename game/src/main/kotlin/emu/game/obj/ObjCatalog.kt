package emu.game.obj

/** Immutable object-type lookup used by authoritative game content. */
fun interface ObjCatalog {
    operator fun get(type: Int): ObjType?

    companion object {
        val EMPTY = ObjCatalog { null }
    }
}
