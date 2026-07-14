package emu.cache.index

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Js5IndexTest {
    @Test fun `protocol 6 named round-trips through encode-decode`() {
        val index = Js5Index(
            protocol = 6,
            revision = 12345,
            flags = Js5IndexFlags.NAMED,
            groups = listOf(
                GroupEntry(
                    id = 1,
                    nameHash = -123456,
                    crc = 0x1234,
                    revision = 7,
                    files = listOf(FileEntry(0, 111), FileEntry(5, 222), FileEntry(9, 333)),
                ),
                GroupEntry(
                    id = 6,
                    nameHash = 987654,
                    crc = 0xABCDEF,
                    revision = 3,
                    files = listOf(FileEntry(2, 444)),
                ),
                GroupEntry(
                    id = 100,
                    nameHash = 0,
                    crc = -1,
                    revision = 0,
                    files = emptyList(),
                ),
            ),
        )

        val encoded = Js5IndexEncoder.encode(index)
        val decoded = Js5IndexDecoder.decode(encoded)
        assertEquals(index, decoded)
    }

    @Test fun `protocol 6 sized (unnamed) round-trips`() {
        val index = Js5Index(
            protocol = 6,
            revision = 1,
            flags = Js5IndexFlags.SIZED,
            groups = listOf(
                GroupEntry(id = 0, crc = 1, revision = 1, compressedSize = 100, decompressedSize = 200, files = listOf(FileEntry(0))),
                GroupEntry(id = 3, crc = 2, revision = 5, compressedSize = 50, decompressedSize = 60, files = listOf(FileEntry(1), FileEntry(2))),
            ),
        )

        val encoded = Js5IndexEncoder.encode(index)
        val decoded = Js5IndexDecoder.decode(encoded)
        assertEquals(index, decoded)
    }

    @Test fun `protocol 5 has no revision int and round-trips`() {
        val index = Js5Index(
            protocol = 5,
            revision = 0,
            flags = 0,
            groups = listOf(
                GroupEntry(id = 0, crc = 1, revision = 1, files = listOf(FileEntry(0))),
            ),
        )

        val encoded = Js5IndexEncoder.encode(index)
        // protocol(1) + flags(1) + count(2) + id-delta(2) + crc(4) + revision(4) + fileCount(2) + fileId(2)
        assertEquals(1 + 1 + 2 + 2 + 4 + 4 + 2 + 2, encoded.size)

        val decoded = Js5IndexDecoder.decode(encoded)
        assertEquals(index, decoded)
    }

    @Test fun `protocol 7 bigSmart round-trips both small and large ids`() {
        val index = Js5Index(
            protocol = 7,
            revision = 99,
            flags = Js5IndexFlags.NAMED or Js5IndexFlags.SIZED,
            groups = listOf(
                GroupEntry(id = 5, nameHash = 1, crc = 1, revision = 1, compressedSize = 1, decompressedSize = 1, files = listOf(FileEntry(3, 1))),
                // delta from 5 to 40000 exceeds 32767, forcing the 4-byte bigSmart branch.
                GroupEntry(id = 40000, nameHash = 2, crc = 2, revision = 2, compressedSize = 2, decompressedSize = 2, files = listOf(FileEntry(70000, 2))),
            ),
        )

        val encoded = Js5IndexEncoder.encode(index)
        val decoded = Js5IndexDecoder.decode(encoded)
        assertEquals(index, decoded)
    }

    @Test fun `rejects out-of-order group ids`() {
        val index = Js5Index(
            protocol = 6,
            revision = 0,
            flags = 0,
            groups = listOf(
                GroupEntry(id = 5, crc = 0, revision = 0, files = emptyList()),
                GroupEntry(id = 5, crc = 0, revision = 0, files = emptyList()),
            ),
        )
        assertFailsWith<IllegalArgumentException> { Js5IndexEncoder.encode(index) }
    }

    @Test fun `rejects unknown flags`() {
        assertFailsWith<IllegalArgumentException> {
            Js5Index(protocol = 6, revision = 0, flags = 0x2, groups = emptyList())
        }
    }

    @Test fun `crc32 is a standard CRC-32`() {
        // "123456789" -> 0xCBF43926 is the canonical CRC-32 check value.
        assertEquals(0xCBF43926.toInt(), crc32("123456789".toByteArray()))
    }

    @Test fun `withRecomputedCrc bumps revision and recomputes crc from the given bytes`() {
        val entry = GroupEntry(id = 1, crc = 0, revision = 5, files = emptyList())
        val newBytes = byteArrayOf(1, 2, 3, 4)
        val updated = entry.withRecomputedCrc(newBytes)
        assertEquals(crc32(newBytes), updated.crc)
        assertEquals(6, updated.revision)
    }
}
