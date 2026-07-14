package emu.protocol.osrs239.game.codec

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerMovement
import emu.protocol.osrs239.game.prot.GameServerProt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Throws on any use, to prove the encoder never touches the cipher (KDoc contract). */
private object PlayerInfoExplodingCipher : StreamCipher {
    override fun nextInt(): Int = error("PlayerInfoEncoder must not consume the cipher")
}

/**
 * Pins PLAYER_INFO (opcode 28) for the minimal single-local-player case, verified against the
 * rev-239 client's own reference GPI decoder (rsprot `PlayerInfoClient`). The high-resolution
 * section encodes the local player as a stationary run (active=0); the low-resolution section skips
 * all 2046 other slots in one stationary run. Sending the local player as an *active* update with
 * movement-opcode 0 (the old encoding) makes the client throw and disconnect.
 */
class PlayerInfoEncoderTest {
    @Test fun `binds to the PLAYER_INFO prot and message type`() {
        assertEquals(GameServerProt.PLAYER_INFO, PlayerInfoEncoder.prot)
        assertEquals(PlayerInfo::class.java, PlayerInfoEncoder.messageType)
    }

    @Test fun `encodes the minimal appearance-less GPI as three bytes`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(appearance = null))

        // High-res section (byte-aligned): active=0, stationary selector 0 -> bits 0 00, padded
        //   -> 0b00000000 = 0x00. (The local player is stationary, encoded as a length-0 run.)
        assertEquals(0x00, body[0].toInt() and 0xFF)
        // Low-res section (byte-aligned): active=0, selector 3 (11-bit count follows), count=2045
        //   -> bits 0 11 11111111101, byte-aligned -> 0x7F 0xF4.
        assertEquals(0x7F, body[1].toInt() and 0xFF)
        assertEquals(0xF4, body[2].toInt() and 0xFF)
        assertEquals(3, body.size)
    }

    @Test fun `an appearance update encodes the local player active with an appearance extended-info block`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(PlayerAppearance(name = "player", combatLevel = 3)))

        // High-res section: active=1, has-extended-info=1, movement opcode 0 -> bits 1 1 00,
        // byte-aligned -> 0b11000000 = 0xC0 (vs 0x00 for the appearance-less stationary case).
        assertEquals(0xC0, body[0].toInt() and 0xFF, "high-res: active+extInfo+idle")
        // Low-res section unchanged: skip all 2045 other slots -> 0x7F 0xF4.
        assertEquals(0x7F, body[1].toInt() and 0xFF)
        assertEquals(0xF4, body[2].toInt() and 0xFF)
        // Extended-info pass: APPEARANCE flag byte (0x20), then the block length as p1Alt3
        // (`128 - len`), inverse of the rev-239 decoder's `g1Alt3 = 128 - raw`.
        assertEquals(0x20, body[3].toInt() and 0xFF, "APPEARANCE extended-info flag")
        val blockLen = (128 - (body[4].toInt() and 0xFF)) and 0xFF
        assertEquals(body.size - 5, blockLen, "g1Alt3 length must match the trailing appearance bytes")

        // Decode the appearance sub-buffer the way the client's decodeAppearance does and confirm
        // the identity fields survive the round-trip.
        // Rev 239 reads the block through gdataAlt2, subtracting 128 from every wire byte.
        val appearance = body.copyOfRange(5, body.size).map { ((it.toInt() and 0xFF) - 128).toByte() }.toByteArray()
        var p = 0
        fun u8() = appearance[p++].toInt() and 0xFF
        assertEquals(0, u8(), "gender male")
        assertEquals(0xFF, u8(), "skull icon none")
        assertEquals(0xFF, u8(), "overhead icon none")
        repeat(PlayerAppearance.EQUIPMENT_SLOT_COUNT) { // equipment identikit slots
            val flag = u8()
            if (flag != 0) u8() // non-empty slot has a second (model-id) byte
        }
        repeat(PlayerAppearance.EQUIPMENT_SLOT_COUNT) { assertEquals(0, u8(), "interface identikit slot empty") }
        repeat(PlayerAppearance.COLOR_COUNT) { u8() }
        repeat(PlayerAppearance.ANIMATION_COUNT) { p += 2 } // 7 anim shorts
        val nameBytes = StringBuilder()
        while (true) { val b = u8(); if (b == 0) break; nameBytes.append(b.toChar()) }
        assertEquals("player", nameBytes.toString())
        assertEquals(3, u8(), "combat level")
    }

    @Test fun `walk movement uses active opcode one and the rev239 three-bit direction`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(movement = PlayerMovement.Walk(1, 0)))

        // active=1, no ext=0, opcode=01, east direction=100, byte pad=0
        assertEquals(0x98, body[0].toInt() and 0xFF)
        assertEquals(3, body.size)
    }

    @Test fun `run movement uses active opcode two and the rev239 four-bit delta`() {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(movement = PlayerMovement.Run(2, 0)))

        // active=1, no ext=0, opcode=10, east-two delta=1000
        assertEquals(0xA8, body[0].toInt() and 0xFF)
        assertEquals(3, body.size)
    }

    @Test fun `cached run speed uses rev239 move-speed flag and signed alt2 byte`() {
        val body = PlayerInfoEncoder.encode(
            NopStreamCipher,
            PlayerInfo(movement = PlayerMovement.Run(2, 0), moveSpeed = 2),
        )

        // active=1, extended=1, opcode=10, east-two=1000
        assertEquals(0xE8, body[0].toInt() and 0xFF)
        assertEquals(listOf(0x08, 0x04), body.slice(3..4).map { it.toInt() and 0xFF })
        // MOVE_SPEED (0x400) widens flags through 0x8; g1sAlt2 is inverted by p1Alt2 (-2).
        assertEquals(0xFE, body[5].toInt() and 0xFF)
        assertEquals(6, body.size)
    }

    @Test fun `temporary movement speed is independent from cached movement speed`() {
        val body = PlayerInfoEncoder.encode(
            NopStreamCipher,
            PlayerInfo(movement = PlayerMovement.Walk(1, 0), moveSpeed = 2, temporaryMoveSpeed = 1),
        )

        assertEquals(0xD8, body[0].toInt() and 0xFF, "active+extended walk east")
        assertEquals(listOf(0x08, 0x14), body.slice(3..4).map { it.toInt() and 0xFF })
        assertEquals(0xFE, body[5].toInt() and 0xFF, "cached run via p1Alt2")
        assertEquals(1, body[6].toInt() and 0xFF, "temporary walk as signed plain byte")
    }

    @Test fun `move speed precedes appearance in rev239 extended-info order`() {
        val body = PlayerInfoEncoder.encode(
            NopStreamCipher,
            PlayerInfo(appearance = PlayerAppearance(name = "player"), moveSpeed = 1),
        )

        assertEquals(0xC0, body[0].toInt() and 0xFF)
        assertEquals(listOf(0x28, 0x04), body.slice(3..4).map { it.toInt() and 0xFF })
        assertEquals(0xFF, body[5].toInt() and 0xFF, "cached walk via p1Alt2")
        val appearanceLength = (128 - (body[6].toInt() and 0xFF)) and 0xFF
        assertEquals(body.size - 7, appearanceLength)
    }

    @Test fun `encode never consumes the cipher`() {
        val body = PlayerInfoEncoder.encode(PlayerInfoExplodingCipher, PlayerInfo(appearance = null))

        assertEquals(3, body.size)
    }

    @Test fun `sanity check PlayerInfoExplodingCipher actually throws when used`() {
        assertFailsWith<IllegalStateException> { PlayerInfoExplodingCipher.nextInt() }
    }
}
