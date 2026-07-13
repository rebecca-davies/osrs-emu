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
}
