package emu.protocol.osrs239.game

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Throws on any use, to prove the encoder never touches the cipher (KDoc contract). */
private object PlayerInfoExplodingCipher : StreamCipher {
    override fun nextInt(): Int = error("PlayerInfoEncoder must not consume the cipher")
}

private const val BIT_SECTION_BYTES = 3 // 20 bits (4 local-player + 8 + 8 list counts), byte-aligned
private const val APPEARANCE_HEADER_BYTES = 2 // mask byte + length byte
private const val DEFAULT_APPEARANCE_BYTES = 50 // see hand-computed layout below

/**
 * Pins the current best-effort interpretation of PLAYER_INFO (opcode 28) for the minimal
 * single-local-player case (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b/§4c).
 *
 * CONFIDENCE: these tests lock in this implementation's *chosen* bit widths / mask value / field
 * order so a later change is caught by a diff — they do NOT assert rev-239 ground truth, which is
 * MEDIUM/LOW confidence per [PlayerInfoEncoder]'s KDoc. A later end-to-end task must correct any
 * assertion here that turns out to disagree with the real client (checked via the `dy.ae`
 * bytes-consumed assertion, `dy.java:120`).
 */
class PlayerInfoEncoderTest {
    @Test fun `binds to the PLAYER_INFO prot and message type`() {
        assertEquals(GameServerProt.PLAYER_INFO, PlayerInfoEncoder.prot)
        assertEquals(PlayerInfo::class.java, PlayerInfoEncoder.messageType)
    }

    @Test fun `leading bitfield marks the local player as updated, stationary, with extended info`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(PlayerAppearance()))

        // bit0=1 (has update), bits1-2=00 (movement type NOTHING), bit3=1 (has extended info),
        // bits4-7=0 (top 4 bits of the 8-bit active-list count) -> 1 0 0 1 0000 = 0x90.
        assertEquals(0x90, body[0].toInt() and 0xFF)
        // remaining 4 bits of the active-list count + top 4 bits of the inactive-list count: all zero.
        assertEquals(0x00, body[1].toInt() and 0xFF)
        // remaining 4 bits of the inactive-list count + 4 zero-padding bits to byte-align: all zero.
        assertEquals(0x00, body[2].toInt() and 0xFF)
    }

    @Test fun `extended-info mask and length header follow the byte-aligned bit section`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(PlayerAppearance()))

        assertEquals(0x10, body[BIT_SECTION_BYTES].toInt() and 0xFF) // placeholder appearance mask bit
        assertEquals(DEFAULT_APPEARANCE_BYTES, body[BIT_SECTION_BYTES + 1].toInt() and 0xFF) // length prefix
    }

    @Test fun `default appearance block matches the hand-computed byte layout`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(PlayerAppearance()))
        val appearance = body.copyOfRange(
            BIT_SECTION_BYTES + APPEARANCE_HEADER_BYTES,
            BIT_SECTION_BYTES + APPEARANCE_HEADER_BYTES + DEFAULT_APPEARANCE_BYTES,
        ).map { it.toInt() and 0xFF }

        val expected = listOf(
            0x00, // gender = male
            0x00, // headIcon = none
            // 12 equipment slots: [hat, cape, amulet, weapon, torso, shield, arms, legs, hair, hands, feet, jaw]
            0x00, // hat
            0x00, // cape
            0x00, // amulet
            0x00, // weapon
            0x01, 0x01, // torso = 0x101
            0x00, // shield
            0x01, 0x02, // arms = 0x102
            0x01, 0x03, // legs = 0x103
            0x01, 0x04, // hair = 0x104
            0x01, 0x05, // hands = 0x105
            0x01, 0x06, // feet = 0x106
            0x00, // jaw
            // 5 colors: all placeholder 0
            0x00, 0x00, 0x00, 0x00, 0x00,
            // 7 animations: all -1 (0xFFFF sentinel = "use client default")
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
            // name = base37("player") = 1_132_078_325 = 0x437A24F5, as an 8-byte big-endian long
            0x00, 0x00, 0x00, 0x00, 0x43, 0x7A, 0x24, 0xF5,
            0x03, // combat level = 3
            0x00, 0x00, // skill level = 0
        )
        assertEquals(expected, appearance)
    }

    @Test fun `total body length is the bit section plus the extended-info header plus the appearance bytes`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(PlayerAppearance()))

        assertEquals(BIT_SECTION_BYTES + APPEARANCE_HEADER_BYTES + DEFAULT_APPEARANCE_BYTES, body.size)
    }

    @Test fun `a non-zero equipment model id encodes as a 2-byte big-endian short`() {
        val appearance = PlayerAppearance(equipment = List(12) { if (it == 4) 0x150 else 0 })
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(appearance))

        val equipStart = BIT_SECTION_BYTES + APPEARANCE_HEADER_BYTES + 2 // after gender + headIcon
        // slots 0-3 are zero (1 byte each), slot 4 (torso) is the non-zero 2-byte short.
        assertEquals(0x01, body[equipStart + 4].toInt() and 0xFF)
        assertEquals(0x50, body[equipStart + 5].toInt() and 0xFF)
    }

    @Test fun `an all-zero equipment set shrinks the appearance length prefix accordingly`() {
        val appearance = PlayerAppearance(equipment = List(12) { 0 })
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(appearance))

        // 12 slots x 1 byte instead of the default's 6x1 + 6x2 = 18 bytes -> 6 bytes shorter.
        assertEquals(DEFAULT_APPEARANCE_BYTES - 6, body[BIT_SECTION_BYTES + 1].toInt() and 0xFF)
    }

    @Test fun `encode never consumes the cipher`() {
        // PlayerInfoExplodingCipher throws on nextInt(); if encode() called it even once this test fails.
        val body = PlayerInfoEncoder.encode(PlayerInfoExplodingCipher, PlayerInfo(PlayerAppearance()))

        assertEquals(BIT_SECTION_BYTES + APPEARANCE_HEADER_BYTES + DEFAULT_APPEARANCE_BYTES, body.size)
    }

    @Test fun `sanity check PlayerInfoExplodingCipher actually throws when used`() {
        assertFailsWith<IllegalStateException> { PlayerInfoExplodingCipher.nextInt() }
    }
}
