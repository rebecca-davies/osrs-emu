package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [PlayerInfo] (opcode 28) as the standard OSRS "GPI" bit stream for the minimal case:
 * only the local player is present, nobody else is updated or added, and the local player is
 * stationary. Reconstructed from the rev-239 decompile (`dy.ae`/`dy.uq`/`dy.ax`) 2026-07-14.
 *
 * **Structure (`dy.ae` runs `uq` then, byte-aligned, the extended-info byte block via `yq`):**
 * the bit stream has two byte-aligned sections — a high-definition (HD) section iterating the
 * players already in-scene (`dy.aa`'s `ae[]` list; after GPI-init that is just the local player)
 * and a low-definition (LD) section iterating everyone else (`aq[]`, the other 2046 slots). Each
 * section is a per-player loop: read `kg(1)` "does this player have an update?"; if 0, read a
 * **skip count** (`kg(2)` selector → `0`=skip 0 / `1`=`kg(5)` / `2`=`kg(8)` / `3`=`kg(11)`, all
 * confirmed in `dy` at the `as()` helper) to fast-forward past a run of non-updated players; if 1,
 * read that player's update via `dy.ax` = `kg(1)` has-extended-info (queues the player for the
 * byte block) then `kg(2)` movement type (`0`=none). Each section is byte-aligned (the client's
 * `xv.av()`) at its end.
 *
 * **Local player:** flagged with an update (`kg(1)=1`) and movement type 0 (stationary). Its
 * has-extended-info bit is 1 iff [PlayerInfo.appearance] is non-null.
 *
 * **Extended-info / appearance:** the appearance block (extended-info mask bit `0x100`, read by
 * `dy.ab`) is, in rev 239, a **serialized sub-buffer** — `xv.ea()` u16, `xv.ej()` id, then a
 * length-prefixed blob deserialized by `kk.af`/`zo.al` into the avatar's kit/model data — not the
 * classic 317-era flat appearance block. It is not yet reproduced here, so this encoder currently
 * ignores a non-null [PlayerInfo.appearance] beyond setting the has-extended bit would require the
 * matching bytes; to stay byte-exact it therefore only supports `appearance == null` (no extended
 * info). Sending that keeps the client in-world with terrain rendered; the avatar has no model
 * until the appearance sub-buffer format is implemented. See the research doc §4c.
 *
 * Per [emu.netcore.pipeline.writePacket]'s keystream-ordering contract, the opcode's own ISAAC
 * adjustment is applied by the pipeline, not here — this method never touches [cipher].
 */
object PlayerInfoEncoder : MessageEncoder<PlayerInfo> {
    override val prot: Prot = GameServerProt.PLAYER_INFO
    override val messageType = PlayerInfo::class.java

    /** Number of low-definition (not-in-scene) player slots after GPI-init: 2047 slots minus the local index. */
    private const val LOW_DEF_PLAYER_COUNT = 2046

    /** `kg(2)` skip selector meaning "an 11-bit count follows" (`dy` `as()` helper). */
    private const val SKIP_SELECTOR_11_BITS = 3

    override fun encode(cipher: StreamCipher, message: PlayerInfo): ByteArray {
        require(message.appearance == null) {
            "PlayerInfoEncoder does not yet reproduce the rev-239 appearance sub-buffer; send PlayerInfo(null)"
        }
        val hasExtended = message.appearance != null

        val bits = BitBuf()

        // --- HD section: the local player (the only in-scene player after GPI-init) ---
        bits.writeBits(1, 1) // local player has an update
        bits.writeBits(1, if (hasExtended) 1 else 0) // dy.ax: has extended info?
        bits.writeBits(2, 0) // dy.ax: movement type 0 = stationary
        alignToByte(bits)

        // --- LD section: every other slot, none added. Skip the whole run in one step. ---
        bits.writeBits(1, 0) // first LD player: no update/add
        bits.writeBits(2, SKIP_SELECTOR_11_BITS) // an 11-bit skip count follows
        bits.writeBits(11, LOW_DEF_PLAYER_COUNT - 1) // skip the remaining slots (this one + N-1 more)
        alignToByte(bits)

        return bits.toByteArray()
    }

    /** Pads the bit stream with zero bits up to the next byte boundary (the client's `xv.av()`). */
    private fun alignToByte(bits: BitBuf) {
        val remainder = bits.bitPosition and 7
        if (remainder != 0) bits.writeBits(8 - remainder, 0)
    }
}
