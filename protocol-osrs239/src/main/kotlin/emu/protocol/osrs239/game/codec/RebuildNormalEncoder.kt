package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.RebuildNormal
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [RebuildNormal] as GPI-init followed by the REBUILD_NORMAL body
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §3b/§3d/§4a):
 *
 * 1. **GPI-init** — bit-packed (MSB-first, via [BitBuf]): 30 bits packing the local player's
 *    absolute coordinate as `(plane shl 28) or (x shl 14) or y` (§3d, `kt.av`/`kt.ag`), followed
 *    by 2047 x 18-bit reference coords, one per other player slot — all zero, since this emulator
 *    does not yet track other players. [BitBuf.toByteArray] byte-aligns (zero-pads) the tail.
 * 2. **Base-zone bytes** (`uk.df`, §3b) — the 13x13 zone grid is centred on the player's zone (a
 *    zone = 8 tiles), so the base (top-left) zone is `(x shr 3) - 6, (y shr 3) - 6`. The
 *    decompiled body reads three u16 fields in this order: a leading value CFR could not resolve
 *    a use for ("result unused by CFR view" — written as `0` here), then base-zone X, then
 *    base-zone Y. **CONFIDENCE MEDIUM** on this exact 3-field layout and on endianness: the doc
 *    (§3b, §8) flags the decompiled reads (`xv.ef`/`xv.ea`) as little-endian with an extra
 *    low-byte "type modifier" that CFR mangled beyond reconstruction. Implemented here as three
 *    plain big-endian u16s (matching this codebase's existing wire convention elsewhere) — verify
 *    byte-for-byte against the real client before relying on this end-to-end; a later task
 *    iterates on it.
 *
 * Per [emu.netcore.pipeline.writePacket]'s keystream-ordering contract, the opcode's own ISAAC
 * adjustment is applied by the pipeline, not here — this method deliberately never touches
 * [cipher], so the body is byte-identical whichever cipher (real ISAAC or
 * [emu.crypto.NopStreamCipher]) drives the connection.
 */
object RebuildNormalEncoder : MessageEncoder<RebuildNormal> {
    override val prot: Prot = GameServerProt.REBUILD_NORMAL
    override val messageType = RebuildNormal::class.java

    private const val OTHER_PLAYER_SLOTS = 2047
    private const val ZONE_SIZE_TILES = 8
    private const val ZONE_GRID_RADIUS = 6

    override fun encode(cipher: StreamCipher, message: RebuildNormal): ByteArray {
        val bits = BitBuf()
        val packedCoord = (message.plane shl 28) or (message.x shl 14) or message.y
        bits.writeBits(30, packedCoord)
        repeat(OTHER_PLAYER_SLOTS) { bits.writeBits(18, 0) }
        val gpiInit = bits.toByteArray()

        val baseZoneX = (message.x / ZONE_SIZE_TILES) - ZONE_GRID_RADIUS
        val baseZoneY = (message.y / ZONE_SIZE_TILES) - ZONE_GRID_RADIUS
        val zone = JagexBuffer.alloc(6)
        zone.writeShort(0) // leading field, purpose unresolved from CFR output (§3b) — written as 0
        zone.writeShort(baseZoneX)
        zone.writeShort(baseZoneY)

        return gpiInit + zone.array
    }
}
