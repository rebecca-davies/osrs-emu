package emu.server.game.world.obj

import emu.cache.def.CacheIntEnumDefinitionCatalog
import emu.cache.store.Store
import emu.game.obj.NamedObjEnumCatalog

/** Resolves cache enums whose integer keys map to named-object type ids. */
class CacheNamedObjEnumCatalog(store: Store) : NamedObjEnumCatalog {
    private val definitions = CacheIntEnumDefinitionCatalog(store)

    override fun get(enumId: Int): List<Int>? {
        val definition = definitions[enumId] ?: return null
        require(definition.keyType == INTEGER && definition.valueType == NAMED_OBJ) {
            "enum $enumId must map integer keys to named objects"
        }
        require(definition.entries.size <= MAX_CATALOGUE_ENTRIES) {
            "enum $enumId exceeds $MAX_CATALOGUE_ENTRIES named objects"
        }
        return definition.entries.map { it.value }
    }

    private companion object {
        const val INTEGER = 'i'
        const val NAMED_OBJ = 'O'
        const val MAX_CATALOGUE_ENTRIES = 1_024
    }
}
