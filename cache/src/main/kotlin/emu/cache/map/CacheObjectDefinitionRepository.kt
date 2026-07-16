package emu.cache.map

import emu.cache.container.Container
import emu.cache.def.DefinitionType
import emu.cache.def.ObjectDefinition
import emu.cache.def.codec.ObjectDefCodec
import emu.cache.group.Group
import emu.cache.index.codec.Js5IndexDecoder
import emu.cache.store.Store
import java.util.concurrent.ConcurrentHashMap

/** Lazily decodes object definitions by their actual child file id in config group 6. */
class CacheObjectDefinitionRepository(store: Store) {
    private val encodedById: Map<Int, ByteArray> = loadEncodedDefinitions(store)
    private val decodedById = ConcurrentHashMap<Int, ObjectDefinition>()

    operator fun get(id: Int): ObjectDefinition? {
        val encoded = encodedById[id] ?: return null
        return decodedById.computeIfAbsent(id) { ObjectDefCodec.decode(id, encoded) }
    }

    private fun loadEncodedDefinitions(store: Store): Map<Int, ByteArray> {
        val archive = DefinitionType.CONFIGS_INDEX
        val group = DefinitionType.OBJECT.group
        val referenceBytes = requireNotNull(store.read(REFERENCE_ARCHIVE, archive)) {
            "cache config reference table is missing"
        }
        val index = Js5IndexDecoder.decode(Container.decode(referenceBytes).data)
        val entry = requireNotNull(index.groups.firstOrNull { it.id == group }) {
            "cache object-definition group is missing"
        }
        val groupBytes = requireNotNull(store.read(archive, group)) {
            "cache object-definition container is missing"
        }
        val filesByPosition = Group.unpack(Container.decode(groupBytes).data, entry.files.size)
        return entry.files.mapIndexed { position, file -> file.id to filesByPosition.getValue(position) }.toMap()
    }

    private companion object {
        const val REFERENCE_ARCHIVE = 255
    }
}
