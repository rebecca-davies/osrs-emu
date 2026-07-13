package emu.protocol.osrs239.game

import emu.buffer.BitBuf
import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot

/**
 * Encodes [PlayerInfo] as the minimal single-local-player PLAYER_INFO body
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b/§4c): a bit-packed local-player
 * block (no movement, has extended info) plus empty other-player lists, byte-aligned, followed by
 * the byte-packed extended-info block carrying just the appearance sub-block.
 *
 * **CONFIDENCE breakdown (this is the hardest structure in the ingame protocol — see §8 of the
 * recon doc):**
 * - **HIGH**: the overall shape — a bit-packed section, byte-align, then a byte-packed
 *   extended-info section (§4b, `dy.ae`'s four-call structure) — and that PLAYER_INFO must include
 *   an appearance block for the avatar to draw at all (§4c).
 * - **MEDIUM**: the exact bit widths chosen for the local-player block (`kg(1)` has-update,
 *   `kg(2)` movement type, `kg(1)` has-extended-info — directly per §4c's minimal-shape
 *   description) and that byte-alignment happens once, after both other-player lists.
 * - **LOW / placeholder**: the 8-bit width chosen for the "active list count" and "inactive list
 *   count" fields. The recon (§4c) only says these lists are empty ("count = 0") for the
 *   zero-other-players case; it does not derive a bit width from the decompile (`dy.fb`/`dy.yq`
 *   are CFR-mangled). 8 bits was chosen as a plausible, simple placeholder — a later task must
 *   confirm (or replace with) the real `dy.fb`/`dy.yq` structure against the running client. Real
 *   OSRS-family GPI typically loops per-slot with an end-of-list sentinel rather than a leading
 *   count; that refinement is deliberately deferred here.
 * - **LOW / placeholder**: the extended-info mask byte value ([APPEARANCE_MASK_PLACEHOLDER]) and
 *   the appearance-sub-block length-prefix convention (`dy.ax`'s mask bit assignment is
 *   unrecoverable from the CFR output). The length-prefix-before-payload technique itself is the
 *   standard, universally-documented RS2-family approach for extended-info sub-blocks.
 * - **MEDIUM/LOW**: [PlayerAppearance]'s field order/values — see its own KDoc.
 *
 * Per [emu.netcore.pipeline.writePacket]'s keystream-ordering contract, the opcode's own ISAAC
 * adjustment is applied by the pipeline, not here — this method deliberately never touches
 * [cipher], so the body is byte-identical whichever cipher (real ISAAC or
 * [emu.crypto.NopStreamCipher]) drives the connection.
 */
object PlayerInfoEncoder : MessageEncoder<PlayerInfo> {
    override val prot: Prot = GameServerProt.PLAYER_INFO
    override val messageType = PlayerInfo::class.java

    /** Placeholder extended-info mask bit meaning "appearance sub-block follows" — see KDoc above. */
    private const val APPEARANCE_MASK_PLACEHOLDER = 0x10

    /** Placeholder bit width for the (currently always-empty) other-player list counts — see KDoc. */
    private const val OTHER_PLAYER_LIST_COUNT_BITS = 8

    override fun encode(cipher: StreamCipher, message: PlayerInfo): ByteArray {
        val bits = BitBuf()
        bits.writeBits(1, 1) // local player: has an update
        bits.writeBits(2, 0) // update type = NOTHING (no movement this tick)
        bits.writeBits(1, 1) // local player: has extended info (the appearance block below)
        bits.writeBits(OTHER_PLAYER_LIST_COUNT_BITS, 0) // active/high-def other-player list: empty
        bits.writeBits(OTHER_PLAYER_LIST_COUNT_BITS, 0) // inactive/low-def other-player list: empty
        val bitSection = bits.toByteArray() // byte-aligns (zero-pads) the tail

        val appearanceBytes = encodeAppearance(message.appearance)
        val extendedInfo = JagexBuffer.alloc(2 + appearanceBytes.size)
        extendedInfo.writeByte(APPEARANCE_MASK_PLACEHOLDER)
        extendedInfo.writeByte(appearanceBytes.size)
        extendedInfo.writeBytes(appearanceBytes)

        return bitSection + extendedInfo.array
    }

    /**
     * Byte-packed appearance sub-block, per [PlayerAppearance]'s field-by-field confidence notes.
     * Equipment slots use the classic RS2-family variable-width encoding: `0` writes as a single
     * zero byte, any other value writes as a 2-byte big-endian short — the client is expected to
     * peek the first byte and only consume a second byte when it is non-zero. This exact technique
     * is stable across the whole RS2 lineage (MEDIUM confidence it applies unchanged to rev 239).
     */
    private fun encodeAppearance(appearance: PlayerAppearance): ByteArray {
        var equipmentBytes = 0
        for (item in appearance.equipment) equipmentBytes += if (item == 0) 1 else 2
        val size = 1 + // gender
            1 + // headIcon
            equipmentBytes +
            appearance.colors.size +
            appearance.animations.size * 2 +
            8 + // base37 name long
            1 + // combat level
            2 // skill level

        val buf = JagexBuffer.alloc(size)
        buf.writeByte(appearance.gender)
        buf.writeByte(appearance.headIcon)
        for (item in appearance.equipment) {
            if (item == 0) buf.writeByte(0) else buf.writeShort(item)
        }
        for (color in appearance.colors) buf.writeByte(color)
        for (anim in appearance.animations) buf.writeShort(anim)
        buf.writeLong(encodeNameBase37(appearance.name))
        buf.writeByte(appearance.combatLevel)
        buf.writeShort(appearance.skillLevel)
        return buf.array
    }

    /**
     * The classic RS2-family base37 name encoding: base-37 digits `a-z` = 1-26, `0-9` = 27-36,
     * anything else contributes `0`; only the first 12 characters count. This algorithm itself is
     * HIGH confidence (unchanged across the whole RS2/OSRS lineage); whether rev 239's
     * player-info actually uses a base37 long (vs. a raw c-string) for the name field is MEDIUM
     * confidence (recon §4c: "name (long/base37 or cstring per rev)").
     */
    private fun encodeNameBase37(name: String): Long {
        var result = 0L
        for (index in 0 until minOf(name.length, 12)) {
            val c = name[index]
            result *= 37L
            result += when {
                c in 'a'..'z' -> 1 + (c - 'a')
                c in 'A'..'Z' -> 1 + (c - 'A')
                c in '0'..'9' -> 27 + (c - '0')
                else -> 0
            }
        }
        return result
    }
}
