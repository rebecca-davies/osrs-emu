package emu.buffer

import kotlin.test.Test
import kotlin.test.assertEquals

class BitBufTest {
    @Test fun `single byte write`() {
        val b = BitBuf()
        b.writeBits(8, 0xAB)
        assertEquals(byteArrayOf(0xAB.toByte()).toList(), b.toByteArray().toList())
    }

    @Test fun `sub-byte writes pack MSB-first and pad low bits with zero`() {
        val b = BitBuf()
        b.writeBits(1, 1)
        b.writeBits(1, 0)
        b.writeBits(1, 1)
        // 101 followed by 5 zero padding bits => 1010_0000 = 0xA0
        assertEquals(byteArrayOf(0xA0.toByte()).toList(), b.toByteArray().toList())
    }

    @Test fun `30-bit all-ones value leaves two padding bits clear`() {
        val b = BitBuf()
        b.writeBits(30, 0x3FFFFFFF)
        assertEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFC.toByte()).toList(),
            b.toByteArray().toList(),
        )
    }

    @Test fun `write crosses a byte boundary`() {
        val b = BitBuf()
        b.writeBits(4, 0xF)
        b.writeBits(8, 0xFF)
        assertEquals(
            byteArrayOf(0xFF.toByte(), 0xF0.toByte()).toList(),
            b.toByteArray().toList(),
        )
    }

    @Test fun `two-bit prefix then 30 zero bits fills exactly four bytes`() {
        val b = BitBuf()
        b.writeBits(2, 0b10)
        b.writeBits(30, 0)
        assertEquals(
            byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00).toList(),
            b.toByteArray().toList(),
        )
    }

    @Test fun `bitPosition tracks total bits written`() {
        val b = BitBuf()
        assertEquals(0, b.bitPosition)
        b.writeBits(3, 0b101)
        assertEquals(3, b.bitPosition)
        b.writeBits(18, 0)
        assertEquals(21, b.bitPosition)
    }

    @Test fun `many 18-bit writes grow the buffer and produce the expected byte count`() {
        val b = BitBuf(initialBytes = 4)
        repeat(2047) { b.writeBits(18, 0x3FFFF) }
        assertEquals(2047 * 18, b.bitPosition)
        val expectedBytes = (2047 * 18 + 7) / 8
        assertEquals(expectedBytes, b.toByteArray().size)
    }

    @Test fun `many 18-bit writes round-trip byte-for-byte against a fresh BitBuf`() {
        // Cross-check: packing the same 18-bit reference value 2047 times one bit-write at a
        // time (1 bit per call) must match packing it 18 bits at a time, verifying the
        // cross-byte carry logic independent of chunk size.
        val chunked = BitBuf()
        val value = 0x2A3AA // arbitrary 18-bit pattern
        repeat(2047) { chunked.writeBits(18, value) }

        val bitwise = BitBuf()
        repeat(2047) {
            for (bit in 17 downTo 0) {
                bitwise.writeBits(1, (value ushr bit) and 1)
            }
        }

        assertEquals(chunked.toByteArray().toList(), bitwise.toByteArray().toList())
    }

    @Test fun `writeBits returns this for chaining`() {
        val b = BitBuf()
        val result = b.writeBits(4, 1).writeBits(4, 2)
        assertEquals(b, result)
        assertEquals(byteArrayOf(0x12).toList(), b.toByteArray().toList())
    }

    @Test fun `count out of range throws`() {
        val b = BitBuf()
        assertThrowsIllegalArgument { b.writeBits(0, 0) }
        assertThrowsIllegalArgument { b.writeBits(33, 0) }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
