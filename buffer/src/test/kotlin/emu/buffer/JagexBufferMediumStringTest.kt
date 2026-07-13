package emu.buffer

import kotlin.test.Test
import kotlin.test.assertEquals

class JagexBufferMediumStringTest {
    @Test fun `medium roundtrips big-endian`() {
        val b = JagexBuffer.alloc(3)
        b.writeMedium(0x0A0B0C)
        assertEquals(byteArrayOf(0x0A, 0x0B, 0x0C).toList(), b.array.toList())
        b.pos = 0
        assertEquals(0x0A0B0C, b.readUMedium())
    }

    @Test fun `medium reads full 24-bit unsigned range`() {
        val b = JagexBuffer(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(0xFFFFFF, b.readUMedium())
    }

    @Test fun `cstring roundtrips as cp1252 with null terminator`() {
        val b = JagexBuffer.alloc(16)
        b.writeCString("hi©")            // includes cp1252 char ©
        assertEquals(0, b.array[b.pos - 1].toInt())  // last written byte is the null terminator
        b.pos = 0
        assertEquals("hi©", b.readCString())
    }

    @Test fun `cstring reads up to the terminator only`() {
        val b = JagexBuffer(byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 0, 'c'.code.toByte()))
        assertEquals("ab", b.readCString())
        assertEquals(3, b.pos)                // consumed 'a','b',null — not 'c'
    }
}
