package emu.cache.map

import emu.cache.container.Container
import emu.cache.group.Group
import emu.cache.index.codec.Js5IndexDecoder
import emu.cache.index.model.GroupEntry
import emu.cache.map.codec.MapLocDecoder
import emu.cache.map.codec.MapTileDecoder
import emu.cache.map.model.MapSquare
import emu.cache.store.Store
import java.util.concurrent.atomic.AtomicReferenceArray

/** Primitive-coordinate lookup over map squares that have already been decoded. */
fun interface PreparedMapSquareLookup {
    fun findPrepared(squareX: Int, squareY: Int): MapSquare?
}

/** Resolves and decodes the files in a packed rev-239 map-square group. */
class CacheMapRepository(private val store: Store) : PreparedMapSquareLookup {
    private val groupsById: Map<Int, GroupEntry> = loadMapIndex()
    private val decodedById = AtomicReferenceArray<MapSquare?>(MAP_SQUARE_COUNT)
    private val loadLocks = Array(LOAD_LOCK_COUNT) { Any() }

    fun load(squareX: Int, squareY: Int): MapSquare =
        requireNotNull(loadOrNull(squareX, squareY)) { "cache map square $squareX,$squareY is missing" }

    /** Returns only a square decoded by prior off-thread preparation. */
    override fun findPrepared(squareX: Int, squareY: Int): MapSquare? {
        if (squareX !in 0..255 || squareY !in 0..255) return null
        return decodedById.get(squareX shl 8 or squareY)
    }

    /** Returns a cached decoded square, or `null` when the cache has no map group for it. */
    fun loadOrNull(squareX: Int, squareY: Int): MapSquare? {
        if (squareX !in 0..255 || squareY !in 0..255) return null
        val groupId = squareX shl 8 or squareY
        decodedById.get(groupId)?.let { return it }
        if (groupsById[groupId] == null) return null
        synchronized(loadLocks[groupId and LOAD_LOCK_MASK]) {
            decodedById.get(groupId)?.let { return it }
            return decode(squareX, squareY, groupId).also { decodedById.set(groupId, it) }
        }
    }

    private fun decode(squareX: Int, squareY: Int, groupId: Int): MapSquare {
        require(squareX in 0..255 && squareY in 0..255) { "map square outside world: $squareX,$squareY" }
        val files = readMapSquareFiles(groupId)
        return MapSquare(
            squareX = squareX,
            squareY = squareY,
            tiles = MapTileDecoder.decode(requireFile(files, TERRAIN_FILE_ID, squareX, squareY)),
            locs = MapLocDecoder.decode(requireFile(files, LOC_FILE_ID, squareX, squareY)),
        )
    }

    private fun readMapSquareFiles(groupId: Int): Map<Int, ByteArray> {
        val entry = requireNotNull(groupsById[groupId]) { "cache map-square group $groupId is missing" }
        val encoded = requireNotNull(store.read(MAPS_ARCHIVE, groupId)) {
            "cache map-square group $groupId has no container"
        }
        val filesByPosition = Group.unpack(Container.decode(encoded).data, entry.files.size)
        return entry.files.mapIndexed { position, file ->
            file.id to filesByPosition.getValue(position)
        }.toMap()
    }

    private fun requireFile(
        files: Map<Int, ByteArray>,
        fileId: Int,
        squareX: Int,
        squareY: Int,
    ): ByteArray = requireNotNull(files[fileId]) {
        "cache map square $squareX,$squareY has no file $fileId"
    }

    private fun loadMapIndex(): Map<Int, GroupEntry> {
        val encoded = requireNotNull(store.read(REFERENCE_ARCHIVE, MAPS_ARCHIVE)) {
            "cache maps reference table is missing"
        }
        val index = Js5IndexDecoder.decode(Container.decode(encoded).data)
        return index.groups.associateBy(GroupEntry::id)
    }

    private companion object {
        const val MAP_SQUARE_COUNT = 1 shl 16
        const val LOAD_LOCK_COUNT = 64
        const val LOAD_LOCK_MASK = LOAD_LOCK_COUNT - 1
        const val MAPS_ARCHIVE = 5
        const val REFERENCE_ARCHIVE = 255
        const val TERRAIN_FILE_ID = 0
        const val LOC_FILE_ID = 1
    }
}
