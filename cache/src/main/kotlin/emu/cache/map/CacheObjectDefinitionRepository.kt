package emu.cache.map

import emu.cache.def.ConfigDefinitionGroup
import emu.cache.def.DefinitionType
import emu.cache.def.ObjectDefinition
import emu.cache.def.codec.ObjectDefCodec
import emu.cache.store.Store
import java.util.concurrent.ConcurrentHashMap

/** Lazily decodes object definitions by their actual child file id in config group 6. */
class CacheObjectDefinitionRepository(store: Store) {
    private val encodedById: Map<Int, ByteArray> =
        ConfigDefinitionGroup.read(store, DefinitionType.OBJECT)
    private val decodedById = ConcurrentHashMap<Int, ObjectDefinition>()

    operator fun get(id: Int): ObjectDefinition? {
        val encoded = encodedById[id] ?: return null
        return decodedById.computeIfAbsent(id) { ObjectDefCodec.decode(id, encoded) }
    }
}
