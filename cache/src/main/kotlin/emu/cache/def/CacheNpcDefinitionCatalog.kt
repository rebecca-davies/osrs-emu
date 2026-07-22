package emu.cache.def

import emu.cache.def.codec.NpcDefCodec
import emu.cache.store.Store

/** Eagerly decoded rev-239 NPC definitions, kept off the game-cycle hot path. */
class CacheNpcDefinitionCatalog(store: Store) {
    val definitions: List<NpcDefinition> =
        ConfigDefinitionGroup.read(store, DefinitionType.NPC)
            .map { (id, data) -> NpcDefCodec.decode(id, data) }
}
