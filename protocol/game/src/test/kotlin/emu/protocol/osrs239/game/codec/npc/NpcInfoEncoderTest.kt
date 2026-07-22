package emu.protocol.osrs239.game.codec.npc

import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.NpcInfoAddition
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcInfoEncoderTest {
    @Test
    fun `small-coordinate fields follow the rev 239 client bit reader order`() {
        val encoded =
            NpcInfoEncoder.encode(
                NpcInfo(
                    locals = listOf(NpcInfoLocal.Idle, NpcInfoLocal.Walk(4), NpcInfoLocal.Remove),
                    additions =
                        listOf(
                            NpcInfoAddition(
                                index = 0x1234,
                                type = 0x0234,
                                deltaX = -3,
                                deltaY = 5,
                                orientation = 6,
                            ),
                        ),
                ),
            )
        val bits = BitReader(encoded)

        assertEquals(3, bits.read(8))
        assertEquals(0, bits.read(1))
        assertEquals(1, bits.read(1))
        assertEquals(1, bits.read(2))
        assertEquals(4, bits.read(3))
        assertEquals(0, bits.read(1))
        assertEquals(1, bits.read(1))
        assertEquals(3, bits.read(2))
        assertTrue(bits.remaining >= CLIENT_ADDITION_GUARD_BITS)
        assertEquals(0x1234, bits.read(16))
        assertEquals(0, bits.read(1))
        assertEquals(0, bits.read(1))
        assertEquals(-3, bits.readSigned(6))
        assertEquals(6, bits.read(3))
        assertEquals(5, bits.readSigned(6))
        assertEquals(1, bits.read(1))
        assertEquals(0x0234, bits.read(14))
        assertTrue(bits.remaining < CLIENT_ADDITION_GUARD_BITS)
        assertEquals(5, bits.remaining)
        assertEquals(0, bits.read(bits.remaining))
        assertEquals(encoded.size, bits.consumedBytes)
    }

    private class BitReader(private val bytes: ByteArray) {
        private var position = 0

        val remaining: Int
            get() = bytes.size * Byte.SIZE_BITS - position

        val consumedBytes: Int
            get() = (position + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS

        fun read(count: Int): Int {
            var value = 0
            repeat(count) {
                val byte = bytes[position ushr 3].toInt()
                value = value shl 1 or (byte ushr (7 - (position and 7)) and 1)
                position++
            }
            return value
        }

        fun readSigned(count: Int): Int {
            val value = read(count)
            val sign = 1 shl (count - 1)
            return if (value and sign == 0) value else value - (1 shl count)
        }
    }

    private companion object {
        const val CLIENT_ADDITION_GUARD_BITS = 28
    }
}
