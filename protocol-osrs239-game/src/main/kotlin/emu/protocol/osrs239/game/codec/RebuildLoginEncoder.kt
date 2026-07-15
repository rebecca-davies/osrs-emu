package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.RebuildLogin
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [RebuildLogin] as GPI-init followed by the REBUILD_NORMAL body:
 *
 * 1. **GPI-init** — bit-packed MSB-first: 30 bits packing the local player's
 *    absolute coordinate as `(plane shl 28) or (x shl 14) or y`, followed by one zero-valued
 *    18-bit reference coordinate for each slot in `1..2047` except
 *    [RebuildLogin.localPlayerIndex]. [BitBuf.toByteArray] zero-pads the tail to a byte boundary.
 * 2. **Zone bytes** — six bytes,
 *    three u16 fields in order: world-area (zero), **centre-zone Y**, then **centre-zone X**.
 *    The zone fields use the client's `xv.ea` transform. `wn.ni`
 *    takes these as the *centre* zone of the 13x13 grid (it spans `centre-6 .. centre+6`), so the
 *    value is `x shr 3`. `xv.ea` decodes a u16 as
 *    `(hi shl 8) or ((lo - 128) and 0xFF)` — big-endian, but the **low byte carries a +128 offset**
 *    and each field is written as `[value shr 8, (value + 128) and 0xFF]`.
 *
 * REBUILD_NORMAL has no XTEA-key section. Only instanced REBUILD_REGION carries inline keys;
 * appending keys here desynchronizes the following stream.
 *
 * The pipeline applies the opcode's ISAAC adjustment; this encoder does not consume [cipher].
 */
object RebuildLoginEncoder : MessageEncoder<RebuildLogin> {
    override val prot: Prot = GameServerProt.REBUILD_NORMAL
    override val messageType = RebuildLogin::class.java

    /** Highest player slot index; the GPI-init reference loop runs `1..PLAYER_SLOTS_MAX`. */
    private const val PLAYER_SLOTS_MAX = 2047

    override fun encode(cipher: StreamCipher, message: RebuildLogin): ByteArray {
        val bits = BitBuf()
        val packedCoord = (message.plane shl 28) or (message.x shl 14) or message.y
        bits.writeBits(30, packedCoord)
        // One 18-bit reference coord per other-player slot: 1..2047 except the local index, which
        // the client's dy.af loop skips (its position came from the 30-bit read above).
        for (slot in 1..PLAYER_SLOTS_MAX) {
            if (slot == message.localPlayerIndex) continue
            bits.writeBits(18, 0)
        }
        val gpiInit = bits.toByteArray()

        return gpiInit + RebuildNormalBodyEncoder.encode(
            centreZoneX = message.x shr 3,
            centreZoneY = message.y shr 3,
            worldArea = 0,
        )
    }
}
