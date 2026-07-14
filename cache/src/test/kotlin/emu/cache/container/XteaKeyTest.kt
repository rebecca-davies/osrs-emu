package emu.cache.container

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XteaKeyTest {
    @Test fun `ZERO is the identity key`() {
        assertTrue(XteaKey.ZERO.isZero)
        assertEquals(listOf(0, 0, 0, 0), XteaKey.ZERO.toIntArray().toList())
    }

    @Test fun `non-zero key is not zero`() {
        assertFalse(XteaKey(1, 0, 0, 0).isZero)
    }

    @Test fun `fromIntArray round-trips words`() {
        val key = XteaKey.fromIntArray(intArrayOf(1, 2, 3, 4))
        assertEquals(XteaKey(1, 2, 3, 4), key)
    }

    @Test fun `fromIntArray rejects wrong length`() {
        assertFailsWith<IllegalArgumentException> { XteaKey.fromIntArray(intArrayOf(1, 2, 3)) }
    }

    @Test fun `fromHex parses 32 hex chars into 4 words`() {
        val key = XteaKey.fromHex("00010203" + "04050607" + "08090a0b" + "0c0d0e0f")
        assertEquals(XteaKey(0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f), key)
    }

    @Test fun `fromHex rejects wrong length`() {
        assertFailsWith<IllegalArgumentException> { XteaKey.fromHex("0001") }
    }
}
