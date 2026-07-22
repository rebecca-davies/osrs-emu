package emu.cache.def

import emu.cache.def.codec.NpcDefCodec
import emu.cache.store.Store

/** Eagerly decoded rev-239 NPC definitions, kept off the game-cycle hot path. */
class CacheNpcDefinitionCatalog(store: Store) {
    val definitions: List<NpcDefinition>
    private val byId: Array<NpcDefinition?>

    init {
        val encoded = ConfigDefinitionGroup.read(store, DefinitionType.NPC)
        definitions = encoded.map { (id, data) -> NpcDefCodec.decode(id, data) }
        val highestId = definitions.maxOfOrNull(NpcDefinition::id) ?: -1
        byId = arrayOfNulls(highestId + 1)
        for (definition in definitions) byId[definition.id] = definition
    }

    operator fun get(id: Int): NpcDefinition? = byId.getOrNull(id)
}
