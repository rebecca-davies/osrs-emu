package emu.protocol.osrs239.game

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val OTHER_PLAYER_SLOTS = 2047
private const val GPI_INIT_BITS = 30 + OTHER_PLAYER_SLOTS * 18
private const val GPI_INIT_BYTES = (GPI_INIT_BITS + 7) / 8 // 4610
private const val ZONE_BYTES = 6
private const val EXPECTED_BODY_SIZE = GPI_INIT_BYTES + ZONE_BYTES // 4616

/** Throws on any use, to prove the encoder never touches the cipher (KDoc contract). */
private object ExplodingCipher : StreamCipher {
    override fun nextInt(): Int = error("RebuildNormalEncoder must not consume the cipher")
}

/** Unpacks the leading 30 MSB-first bits of a byte array back into (plane, x, y), mirroring `kt.av`. */
private fun decodePacked30(body: ByteArray): Triple<Int, Int, Int> {
    var acc = 0L
    for (i in 0 until 4) acc = (acc shl 8) or (body[i].toLong() and 0xFF)
    val packed = (acc ushr 2).toInt() // drop the 2 trailing bits of byte[3] (padding)
    val plane = (packed ushr 28) and 0x3
    val x = (packed ushr 14) and 0x3FFF
    val y = packed and 0x3FFF
    return Triple(plane, x, y)
}

class RebuildNormalEncoderTest {
    @Test fun `binds to the REBUILD_NORMAL prot and message type`() {
        assertEquals(GameServerProt.REBUILD_NORMAL, RebuildNormalEncoder.prot)
        assertEquals(RebuildNormal::class.java, RebuildNormalEncoder.messageType)
    }

    @Test fun `leading 30 bits decode back to plane, x, y`() {
        val body = RebuildNormalEncoder.encode(NopStreamCipher, RebuildNormal(plane = 0, x = 3222, y = 3218))

        val (plane, x, y) = decodePacked30(body)
        assertEquals(0, plane)
        assertEquals(3222, x)
        assertEquals(3218, y)
    }

    @Test fun `leading bytes match the hand-computed packed coordinate for Lumbridge`() {
        val body = RebuildNormalEncoder.encode(NopStreamCipher, RebuildNormal(plane = 0, x = 3222, y = 3218))

        // packedCoord = (0 shl 28) or (3222 shl 14) or 3218 = 52_792_466 = 0x0324_58C9_2 truncated to
        // 30 bits -> binary 000011001001011000110010010010, grouped MSB-first into bytes:
        // 0x0C 0x96 0x32 0x48 (byte[3]'s low 2 bits are the start of the padded other-player list).
        assertEquals(0x0C, body[0].toInt() and 0xFF)
        assertEquals(0x96, body[1].toInt() and 0xFF)
        assertEquals(0x32, body[2].toInt() and 0xFF)
        assertEquals(0x48, body[3].toInt() and 0xFF)
    }

    @Test fun `body length is GPI-init (byte-padded) plus the 6 base-zone bytes`() {
        val body = RebuildNormalEncoder.encode(NopStreamCipher, RebuildNormal(plane = 0, x = 3222, y = 3218))

        assertEquals(EXPECTED_BODY_SIZE, body.size)
    }

    @Test fun `every other-player reference slot is zero bits, so the padding tail before the zone bytes is all zero`() {
        val body = RebuildNormalEncoder.encode(NopStreamCipher, RebuildNormal(plane = 0, x = 3222, y = 3218))

        // Bytes [4, GPI_INIT_BYTES) carry only zero-valued 18-bit reference entries (and the final
        // byte's zero-padding), so the whole span must be zero.
        for (i in 4 until GPI_INIT_BYTES) {
            assertEquals(0, body[i].toInt(), "byte $i expected zero (unset other-player reference)")
        }
    }

    @Test fun `base-zone bytes are the 6-byte tail, computed as (coord div 8) minus 6`() {
        val body = RebuildNormalEncoder.encode(NopStreamCipher, RebuildNormal(plane = 0, x = 3222, y = 3218))

        val zone = body.copyOfRange(GPI_INIT_BYTES, GPI_INIT_BYTES + ZONE_BYTES)
        // [hint u16 = 0][baseZoneX u16][baseZoneY u16], big-endian.
        val baseZoneX = (3222 / 8) - 6 // 396
        val baseZoneY = (3218 / 8) - 6 // 396
        assertEquals(
            listOf(0, 0, (baseZoneX ushr 8) and 0xFF, baseZoneX and 0xFF, (baseZoneY ushr 8) and 0xFF, baseZoneY and 0xFF),
            zone.map { it.toInt() and 0xFF },
        )
    }

    @Test fun `different coordinates produce different base-zone bytes`() {
        val body = RebuildNormalEncoder.encode(NopStreamCipher, RebuildNormal(plane = 0, x = 3200, y = 3100))

        val zone = body.copyOfRange(GPI_INIT_BYTES, GPI_INIT_BYTES + ZONE_BYTES)
        val baseZoneX = (3200 / 8) - 6 // 394
        val baseZoneY = (3100 / 8) - 6 // 381
        assertEquals(
            listOf(0, 0, (baseZoneX ushr 8) and 0xFF, baseZoneX and 0xFF, (baseZoneY ushr 8) and 0xFF, baseZoneY and 0xFF),
            zone.map { it.toInt() and 0xFF },
        )
    }

    @Test fun `encode never consumes the cipher`() {
        // ExplodingCipher throws on nextInt(); if encode() called it even once this test fails.
        val body = RebuildNormalEncoder.encode(ExplodingCipher, RebuildNormal(plane = 0, x = 3222, y = 3218))

        assertEquals(EXPECTED_BODY_SIZE, body.size)
    }

    @Test fun `sanity check ExplodingCipher actually throws when used`() {
        assertFailsWith<IllegalStateException> { ExplodingCipher.nextInt() }
    }
}
