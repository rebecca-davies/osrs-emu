package emu.cache.group

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GroupTest {
    @Test fun `single-file group is a raw passthrough`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val files = Group.unpack(payload, fileCount = 1)
        assertEquals(1, files.size)
        assertContentEquals(payload, files.getValue(0))

        val repacked = Group.pack(mapOf(0 to payload))
        assertContentEquals(payload, repacked)
    }

    @Test fun `unpacks a hand-built 3-file stripe group`() {
        val file0 = byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())
        val file1 = byteArrayOf('D'.code.toByte(), 'E'.code.toByte())
        val file2 = byteArrayOf('F'.code.toByte(), 'G'.code.toByte(), 'H'.code.toByte(), 'I'.code.toByte())

        // chunks = 1: content, then delta-encoded sizes (3, 2-3=-1, 4-2=2), then chunk count (1).
        val bytes = file0 + file1 + file2 +
            intBytes(3) + intBytes(-1) + intBytes(2) +
            byteArrayOf(1)

        val files = Group.unpack(bytes, fileCount = 3)
        assertEquals(3, files.size)
        assertContentEquals(file0, files.getValue(0))
        assertContentEquals(file1, files.getValue(1))
        assertContentEquals(file2, files.getValue(2))
    }

    @Test fun `pack then unpack round-trips a multi-file group`() {
        val original = mapOf(
            0 to byteArrayOf(10, 11, 12),
            1 to byteArrayOf(20),
            2 to byteArrayOf(30, 31, 32, 33, 34),
        )

        val packed = Group.pack(original)
        val unpacked = Group.unpack(packed, fileCount = original.size)

        assertEquals(original.keys, unpacked.keys)
        for (id in original.keys) {
            assertContentEquals(original.getValue(id), unpacked.getValue(id))
        }
    }

    @Test fun `pack produces byte-identical output to a hand-built stripe`() {
        val file0 = byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())
        val file1 = byteArrayOf('D'.code.toByte(), 'E'.code.toByte())
        val file2 = byteArrayOf('F'.code.toByte(), 'G'.code.toByte(), 'H'.code.toByte(), 'I'.code.toByte())

        val expected = file0 + file1 + file2 +
            intBytes(3) + intBytes(-1) + intBytes(2) +
            byteArrayOf(1)

        val packed = Group.pack(mapOf(0 to file0, 1 to file1, 2 to file2))
        assertContentEquals(expected, packed)
    }

    private fun intBytes(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(),
        (v ushr 16).toByte(),
        (v ushr 8).toByte(),
        v.toByte(),
    )
}
