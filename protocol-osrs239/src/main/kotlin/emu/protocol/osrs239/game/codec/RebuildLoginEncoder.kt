package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.RebuildLogin
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [RebuildLogin] as GPI-init followed by the REBUILD_NORMAL body
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §3b/§3d/§4a):
 *
 * 1. **GPI-init** — bit-packed (MSB-first, via [BitBuf]): 30 bits packing the local player's
 *    absolute coordinate as `(plane shl 28) or (x shl 14) or y` (§3d, `kt.av`/`kt.ag`), followed
 *    by one 18-bit reference coord (all zero, since this emulator does not yet track other players)
 *    for every player slot in `1..2047` **except** [RebuildLogin.localPlayerIndex] — the client's
 *    `dy.af` loop reads 30 bits for the local slot then `for (n=1; n<2048; n++) if (n!=di) read18`,
 *    i.e. exactly one slot fewer than 2047. Emitting 2047 (as the first cut did) leaves the bit
 *    stream 18 bits too long, which byte-aligns two bytes past where the client reads the zone
 *    fields → the scene loads at a garbage base and the game thread throws
 *    `ArrayIndexOutOfBounds` indexing the local player's absolute X into the (too-small) scene
 *    arrays. [BitBuf.toByteArray] byte-aligns (zero-pads) the tail, matching the client's
 *    `xv.av()` byte-align.
 * 2. **Zone bytes** (`uk.df`, §3b — VERIFIED against the rev-239 decompile 2026-07-14) — six bytes,
 *    three u16 fields in order: world-area (zero), **centre-zone Y**, then **centre-zone X**.
 *    The zone fields use the client's `xv.ea` transform. `wn.ni`
 *    takes these as the *centre* zone of the 13x13 grid (it spans `centre-6 .. centre+6`), so the
 *    value is `x shr 3` (NOT the base/top-left zone `-6`). `xv.ea` decodes a u16 as
 *    `(hi shl 8) or ((lo - 128) and 0xFF)` — big-endian, but the **low byte carries a +128 offset**
 *    (the classic RS "type-A" transform). So each field is written as `[value shr 8, (value + 128)
 *    and 0xFF]`. **CONFIDENCE HIGH** — decode primitives `xm.ea`/`xm.ef` and `wn.ni`/`kt.ax`
 *    (`zone = kt.ax = n shr 3`) were read directly from the decompiled client.
 *
 * **No XTEA key section (verified from the rev-239 decompile 2026-07-14).** The client's
 * REBUILD_NORMAL body reader `uk.df` (`clean-239/uk.java:527`) reads exactly the three `u16`s above
 * and nothing else — `wn.ni`/`qu.da` are not passed the buffer, so no per-mapsquare XTEA keys are
 * read here. (Only the *instanced* REBUILD_REGION, op 125 `lp.dm`→`wn.hg`, carries inline keys.)
 * rev-239 delivers loc keys via the JS5 cache layer (`uf.ez` decrypts only when a group's stored
 * key is non-zero), NOT this packet — so this encoder must NOT append a key loop (doing so would
 * desync the client, which would read those bytes as GPI/zone data). See
 * docs/superpowers/research/2026-07-14-map-xtea-keys.md and [emu.cache.container.MapXteaKeys].
 *
 * Per [emu.netcore.pipeline.writePacket]'s keystream-ordering contract, the opcode's own ISAAC
 * adjustment is applied by the pipeline, not here — this method deliberately never touches
 * [cipher], so the body is byte-identical whichever cipher (real ISAAC or
 * [emu.crypto.NopStreamCipher]) drives the connection.
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
