package emu.game.npc

import emu.game.varp.PlayerVariableValues

/** Immutable NPC-type lookup used by authoritative game content. */
fun interface NpcCatalog {
    operator fun get(type: Int): NpcType?

    /** Resolves this player's effective NPC type through a bounded cache-transform chain. */
    fun resolve(type: NpcType, variables: PlayerVariableValues): NpcType? {
        var current = type
        repeat(MAX_TRANSFORM_DEPTH) {
            val transform = current.transform ?: return current
            val destination = transform.destination(variables)
            if (destination == -1) return null
            current = get(destination) ?: return null
        }
        return null
    }

    companion object {
        val EMPTY = NpcCatalog { null }

        private const val MAX_TRANSFORM_DEPTH = 16
    }
}
