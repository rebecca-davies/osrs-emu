package emu.cache.def

import emu.cache.container.Container
import emu.cache.group.Group
import emu.cache.index.codec.Js5IndexDecoder
import emu.cache.store.Store

/** Reads one cache config group while preserving its real child file identifiers. */
internal object ConfigDefinitionGroup {
    fun read(store: Store, type: DefinitionType): Map<Int, ByteArray> {
        val archive = DefinitionType.CONFIGS_INDEX
        val referenceBytes = requireNotNull(store.read(REFERENCE_ARCHIVE, archive)) {
            "cache config reference table is missing"
        }
        val index = Js5IndexDecoder.decode(Container.decode(referenceBytes).data)
        val entry = requireNotNull(index.groups.firstOrNull { it.id == type.group }) {
            "cache ${type.name.lowercase()}-definition group is missing"
        }
        val groupBytes = requireNotNull(store.read(archive, type.group)) {
            "cache ${type.name.lowercase()}-definition container is missing"
        }
        val filesByPosition = Group.unpack(Container.decode(groupBytes).data, entry.files.size)
        return entry.files
            .mapIndexed { position, file -> file.id to filesByPosition.getValue(position) }
            .toMap()
    }

    private const val REFERENCE_ARCHIVE = 255
}
