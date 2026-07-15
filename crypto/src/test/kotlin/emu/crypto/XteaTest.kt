package emu.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class XteaTest {
    private val key = intArrayOf(0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f)

    @Test fun `encrypt then decrypt is identity`() {
        val original = intArrayOf(0x12345678, 0x0f0e0d0c.toInt(), 0x1a2b3c4d, 0x00000000)
        val work = original.copyOf()
        Xtea.encrypt(work, key)
        assertFalse(work.contentEquals(original), "encryption should change the data")
        Xtea.decrypt(work, key)
        assertContentEquals(original, work)
    }

    @Test fun `known answer vector`() {
        // Independent standard 32-round XTEA vector for a zero key and zero block.
        val work = intArrayOf(0, 0)
        Xtea.encrypt(work, intArrayOf(0, 0, 0, 0))
        assertEquals(0xDEE9D4D8.toInt(), work[0])
        assertEquals(0xF7131ED9.toInt(), work[1])
    }

    @Test fun `byte convenience leaves trailing partial block`() {
        val bytes = ByteArray(12) { it.toByte() } // one 8-byte block + 4 trailing
        val enc = Xtea.encrypt(bytes.copyOf(), key)
        // trailing 4 bytes (indices 8..11) untouched by encryption
        assertContentEquals(bytes.copyOfRange(8, 12), enc.copyOfRange(8, 12))
        val dec = Xtea.decrypt(enc, key)
        assertContentEquals(bytes, dec)
    }
}
