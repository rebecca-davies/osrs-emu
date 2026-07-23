package emu.protocol.osrs239.game.codec.npc

import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.NpcInfoAddition
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal
import emu.protocol.osrs239.game.message.npc.NpcInfoUpdate
import emu.protocol.osrs239.game.message.npc.NpcSequence
import emu.protocol.osrs239.game.message.entity.InfoHeadbar
import emu.protocol.osrs239.game.message.entity.InfoHitmark
import emu.protocol.osrs239.game.message.entity.InfoSpotAnimation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NpcInfoEncoderTest {
    @Test
    fun `small-coordinate fields follow the rev 239 client bit reader order`() {
        val encoded =
            NpcInfoEncoder.encode(
                NpcInfo(
                    locals = listOf(NpcInfoLocal.Idle, NpcInfoLocal.Walk.EAST, NpcInfoLocal.Remove),
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

    @Test
    fun `extended blocks follow the rev 239 flags transforms and client order`() {
        val update =
            NpcInfoUpdate(
                sequence = NpcSequence(0x1234, delay = 5),
                hitmarks = listOf(InfoHitmark(type = 13, value = 7, delay = 2)),
                headbars = listOf(InfoHeadbar(type = 0, startFill = 15)),
                spotAnimations =
                    listOf(InfoSpotAnimation(slot = 0, id = 0x5678, height = 9, delay = 10)),
            )

        val encoded =
            NpcInfoEncoder.encode(
                NpcInfo(
                    locals = listOf(NpcInfoLocal.Extended(NpcInfoLocal.Idle, update)),
                    additions = emptyList(),
                ),
            )

        assertContentEquals(
            byteArrayOf(
                0x01,
                0x9F.toByte(),
                0xFF.toByte(),
                0xE0.toByte(),
                0xC0.toByte(),
                0x08,
                0x2C,
                0x01,
                0x81.toByte(),
                13,
                7,
                2,
                4,
                0xFF.toByte(),
                0,
                0,
                0,
                0x8F.toByte(),
                0x12,
                0x34,
                0xFB.toByte(),
                0xFF.toByte(),
                0,
                0x56,
                0x78,
                0,
                10,
                0,
                9,
            ),
            encoded,
        )
    }

    @Test
    fun `additions are bounded by the retained client list`() {
        val additions =
            List(NpcInfo.MAX_LOCAL_NPCS) { index ->
                NpcInfoAddition(index, type = 1, deltaX = 0, deltaY = 0, orientation = 0)
            }

        val encoded = NpcInfoEncoder.encode(NpcInfo(emptyList(), additions))

        assertTrue(encoded.size <= 0xFFFF)
        assertFailsWith<IllegalArgumentException> {
            NpcInfo(emptyList(), additions + NpcInfoAddition(300, 1, 0, 0, 0))
        }
        assertFailsWith<IllegalArgumentException> {
            NpcInfo(listOf(NpcInfoLocal.Idle), additions)
        }
        assertFailsWith<IllegalArgumentException> {
            NpcInfo(emptyList(), listOf(additions.first(), additions.first()))
        }
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
