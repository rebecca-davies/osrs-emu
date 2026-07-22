package emu.server.game.world.npc

import emu.cache.def.NpcDefinition
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcType

/** Maps cache NPC definitions into the immutable type model owned by game. */
class CacheNpcCatalog(definitions: Iterable<NpcDefinition>) : NpcCatalog {
    private val types = arrayOfNulls<NpcType>(MAX_NPC_TYPE + 1)

    init {
        for (definition in definitions) {
            val name = definition.name?.takeIf(String::isNotBlank) ?: continue
            if (definition.id !in types.indices) continue
            types[definition.id] =
                NpcType(
                    id = definition.id,
                    name = name,
                    size = definition.size ?: DEFAULT_SIZE,
                )
        }
    }

    override fun get(type: Int): NpcType? = types.getOrNull(type)

    private companion object {
        const val MAX_NPC_TYPE = (1 shl 14) - 1
        const val DEFAULT_SIZE = 1
    }
}
