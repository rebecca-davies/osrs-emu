package emu.protocol.osrs239.game.codec

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
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

    @Test fun `a non-null appearance is rejected until the rev-239 appearance sub-buffer is implemented`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerInfoEncoder.encode(NopStreamCipher, PlayerInfo(PlayerAppearance()))
        }
    }

    @Test fun `encode never consumes the cipher`() {
        val body = PlayerInfoEncoder.encode(PlayerInfoExplodingCipher, PlayerInfo(appearance = null))

        assertEquals(3, body.size)
    }

    @Test fun `sanity check PlayerInfoExplodingCipher actually throws when used`() {
        assertFailsWith<IllegalStateException> { PlayerInfoExplodingCipher.nextInt() }
    }
}
