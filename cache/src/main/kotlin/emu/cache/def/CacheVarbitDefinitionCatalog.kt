package emu.cache.def

import emu.cache.def.codec.VarbitDefCodec
import emu.cache.store.Store

/** Eagerly decoded rev-239 varbit definitions, kept off the game-cycle hot path. */
class CacheVarbitDefinitionCatalog(store: Store) {
    val definitions: List<VarbitDefinition> =
        ConfigDefinitionGroup.read(store, DefinitionType.VARBIT)
            .map { (id, data) -> VarbitDefCodec.decode(id, data) }
}
