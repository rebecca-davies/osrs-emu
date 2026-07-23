package emu.cache.def

import emu.cache.def.codec.IntEnumDefCodec
import emu.cache.store.Store

/** Lazily decodes bounded integer-valued cache enums from the CONFIGS index. */
class CacheIntEnumDefinitionCatalog(store: Store) {
    private val files = ConfigDefinitionGroup.read(store, DefinitionType.ENUM)

    operator fun get(id: Int): IntEnumDefinition? = files[id]?.let { IntEnumDefCodec.decode(id, it) }
}
