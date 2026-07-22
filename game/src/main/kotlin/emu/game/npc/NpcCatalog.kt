package emu.game.npc

/** Immutable NPC-type lookup used by authoritative game content. */
fun interface NpcCatalog {
    operator fun get(type: Int): NpcType?

    companion object {
        val EMPTY = NpcCatalog { null }
    }
}
