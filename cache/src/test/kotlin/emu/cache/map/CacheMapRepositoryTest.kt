package emu.cache.map

import emu.cache.container.Container
import emu.cache.container.Js5Compression
import emu.cache.def.ObjectDefinition
import emu.cache.def.codec.ObjectDefCodec
import emu.cache.group.Group
import emu.cache.index.FileEntry
import emu.cache.index.GroupEntry
import emu.cache.index.Js5Index
import emu.cache.index.Js5IndexEncoder
import emu.cache.index.Js5IndexFlags
import emu.cache.store.Store
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CacheMapRepositoryTest {
    @Test
    fun `loads terrain and loc files from a packed rev 239 map-square group`() {
        val terrain = ByteArray(4 * 64 * 64 * 2 + 1)
        val locs = byteArrayOf(0)
        val mapSquareId = 50 shl 8 or 50
        val mapFiles = mapOf(
            0 to terrain,
            1 to locs,
            2 to byteArrayOf(),
            3 to byteArrayOf(),
            4 to byteArrayOf(),
        )
        val mapGroup = GroupEntry(
            id = mapSquareId,
            crc = 0,
            revision = 0,
            files = mapFiles.keys.map(::FileEntry),
        )
        val store = MemoryStore(
            mapOf(
                (255 to 5) to indexContainer(listOf(mapGroup), named = false),
                (5 to mapSquareId) to Container.encode(Js5Compression.NONE, Group.pack(mapFiles)),
            ),
        )

        val square = CacheMapRepository(store).load(50, 50)

        assertEquals(50, square.squareX)
        assertEquals(50, square.squareY)
        assertEquals(0, square.tiles[0, 0, 0])
        assertEquals(emptyList(), square.locs)
    }

    @Test
    fun `missing squares return null and decoded squares are cached`() {
        val terrain = ByteArray(4 * 64 * 64 * 2 + 1)
        val mapSquareId = 50 shl 8 or 50
        val mapFiles = mapOf(0 to terrain, 1 to byteArrayOf(0))
        val mapGroup = GroupEntry(
            id = mapSquareId,
            crc = 0,
            revision = 0,
            files = mapFiles.keys.map(::FileEntry),
        )
        val repository = CacheMapRepository(
            MemoryStore(
                mapOf(
                    (255 to 5) to indexContainer(listOf(mapGroup), named = false),
                    (5 to mapSquareId) to Container.encode(Js5Compression.NONE, Group.pack(mapFiles)),
                ),
            ),
        )

        assertNull(repository.loadOrNull(49, 49))
        val decoded = requireNotNull(repository.loadOrNull(50, 50))
        assertSame(decoded, repository.loadOrNull(50, 50))
    }

    @Test
    fun `object repository maps striped group positions back to definition file ids`() {
        val first = ObjectDefinition(id = 100, name = "wall", sizeX = 2)
        val second = ObjectDefinition(id = 250, name = "tree", interactType = 0)
        val files = mapOf(
            100 to ObjectDefCodec.encode(first),
            250 to ObjectDefCodec.encode(second),
        )
        val configGroup = GroupEntry(
            id = 6,
            crc = 0,
            revision = 0,
            files = files.keys.map(::FileEntry),
        )
        val store = MemoryStore(
            mapOf(
                (255 to 2) to indexContainer(listOf(configGroup), named = false),
                (2 to 6) to Container.encode(Js5Compression.NONE, Group.pack(files)),
            ),
        )

        val definitions = CacheObjectDefinitionRepository(store)

        assertEquals(first, definitions[100])
        assertEquals(second, definitions[250])
        assertEquals(null, definitions[999])
    }

    private fun indexContainer(groups: List<GroupEntry>, named: Boolean = true): ByteArray {
        val index = Js5Index(
            protocol = 7,
            revision = 1,
            flags = if (named) Js5IndexFlags.NAMED else 0,
            groups = groups,
        )
        return Container.encode(Js5Compression.NONE, Js5IndexEncoder.encode(index))
    }

    private class MemoryStore(private val data: Map<Pair<Int, Int>, ByteArray>) : Store {
        override fun read(archive: Int, group: Int): ByteArray? = data[archive to group]
    }
}
