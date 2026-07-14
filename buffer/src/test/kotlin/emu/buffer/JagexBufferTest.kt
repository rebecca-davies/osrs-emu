package emu.buffer

import kotlin.test.Test
import kotlin.test.assertEquals

class JagexBufferTest {
    @Test fun `int roundtrips big-endian`() {
        val b = JagexBuffer.alloc(4)
        b.writeInt(0x01020304)
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), b.array.toList())
        b.pos = 0
        assertEquals(0x01020304, b.readInt())
    }

    @Test fun `unsigned byte reads 0xFF as 255`() {
        val b = JagexBuffer(byteArrayOf(0xFF.toByte()))
        assertEquals(255, b.readUByte())
    }

    @Test fun `long roundtrips`() {
        val b = JagexBuffer.alloc(8)
        b.writeLong(0x1122334455667788L)
        b.pos = 0
        assertEquals(0x1122334455667788L, b.readLong())
    }

    @Test fun `readableBytes tracks position`() {
        val b = JagexBuffer(ByteArray(10))
        assertEquals(10, b.readableBytes())
        b.readInt()
        assertEquals(6, b.readableBytes())
    }

    @Test fun `alternate writes match Jagex p1 p2 and p4 byte transforms`() {
        val b = JagexBuffer.alloc(3 + 6 + 12)
        b.writeByteAlt1(2)
        b.writeByteAlt2(2)
        b.writeByteAlt3(2)
        b.writeShortAlt1(0x1234)
        b.writeShortAlt2(0x1234)
        b.writeShortAlt3(0x1234)
        b.writeIntAlt1(0x12345678)
        b.writeIntAlt2(0x12345678)
        b.writeIntAlt3(0x12345678)

        assertEquals(
            listOf(
                0x82, 0xFE, 0x7E,
                0x34, 0x12, 0x12, 0xB4, 0xB4, 0x12,
                0x78, 0x56, 0x34, 0x12,
                0x56, 0x78, 0x12, 0x34,
                0x34, 0x12, 0x78, 0x56,
            ),
            b.array.map { it.toInt() and 0xFF },
        )
    }
}
