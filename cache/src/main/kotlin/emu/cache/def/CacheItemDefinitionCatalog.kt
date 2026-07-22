package emu.cache.def

import emu.cache.def.codec.ItemDefCodec
import emu.cache.store.Store

/** Eagerly decoded rev-239 item definitions, kept off the game-cycle hot path. */
class CacheItemDefinitionCatalog(store: Store) {
    val definitions: List<ItemDefinition> =
        ConfigDefinitionGroup.read(store, DefinitionType.ITEM)
            .map { (id, data) -> ItemDefCodec.decode(id, data) }
}
