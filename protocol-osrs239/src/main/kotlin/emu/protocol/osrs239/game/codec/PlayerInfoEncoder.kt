package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [PlayerInfo] (opcode 28) as the rev-239 OSRS "GPI" bit stream for the minimal case: only
 * the local player is present, nobody else is added, and everyone is stationary. Verified against
 * the rev-239 client's own reference GPI decoder (rsprot `osrs-239` `PlayerInfoClient.decodeBitCodes`)
 * and driven against the running client 2026-07-14 (it reaches LOGGED_IN and holds).
 *
 * **Structure — the rev-239 GPI is four byte-aligned bit sections, in this order** (client
 * `decodeBitCodes`; the server encoder is rsprot `PlayerInfo.pBitcodes`):
 *  1. high-resolution players whose flag was "active last cycle",
 *  2. high-resolution players whose flag was "inactive/stationary last cycle",
 *  3. low-resolution players "inactive last cycle",
 *  4. low-resolution players "active last cycle".
 *
 * Each section is a per-player loop reading `gBits(1)` "active?": a `0` reads a **stationary run**
 * (`gBits(2)` selector → `0`=skip 0 / `1`=`gBits(5)` / `2`=`gBits(8)` / `3`=`gBits(11)`) that
 * fast-forwards past that many further non-updating players; a `1` reads that player's movement +
 * extended-info bits. Each section byte-aligns at its end, and a section with no qualifying players
 * emits **zero** bytes.
 *
 * After GPI-init (sent inside REBUILD_NORMAL) the client has exactly one high-resolution player (the
 * local avatar) and 2046 low-resolution players (every other slot). Which of the paired sections
 * carries them flips each cycle as their flags toggle, but empty sections cost no bytes and the
 * non-empty ones are byte-identical, so **the wire body is the same every tick**: a stationary local
 * player followed by a single stationary run skipping the remaining 2045 low-resolution slots.
 *
 * **Local player = a stationary run, NOT an active update.** Emitting the local player as `active=1`
 * with movement-opcode 0 and no extended info makes the client's `getHighResolutionPlayerPosition`
 * hit `if (opcode == 0) ... else if (localIndex == idx) throw` and drop the connection instantly —
 * the milestone-4→5 disconnect. Encoding it as `active=0` (a stationary run of length 0) is the
 * correct no-movement update.
 *
 * **Extended-info / appearance:** the appearance block is, in rev 239, a serialized sub-buffer
 * appended after the four bit sections; it is not yet reproduced here, so this encoder supports only
 * `appearance == null` (no extended info). Sending that keeps the client in-world with terrain
 * rendered and the connection held; the avatar simply has no model until the appearance sub-buffer
 * format is implemented. See the research doc §4c.
 *
 * Per [emu.netcore.pipeline.writePacket]'s keystream-ordering contract, the opcode's own ISAAC
 * adjustment is applied by the pipeline, not here — this method never touches [cipher].
 */
object PlayerInfoEncoder : MessageEncoder<PlayerInfo> {
    override val prot: Prot = GameServerProt.PLAYER_INFO
    override val messageType = PlayerInfo::class.java

    /** Low-resolution (not-in-scene) player slots after GPI-init: 2047 slots minus the local index. */
    private const val LOW_RES_PLAYER_COUNT = 2046

    /** Stationary-run selector meaning "an 11-bit count follows" (client `readStationary`). */
    private const val STATIONARY_SELECTOR_11_BITS = 3

    override fun encode(cipher: StreamCipher, message: PlayerInfo): ByteArray {
        require(message.appearance == null) {
            "PlayerInfoEncoder does not yet reproduce the rev-239 appearance sub-buffer; send PlayerInfo(null)"
        }

        val bits = BitBuf()

        // High-resolution section: the local player, stationary. active=0 opens a stationary run,
        // selector 0 => skip 0 further players (the local avatar is the only high-res player).
        bits.writeBits(1, 0) // active? no -> stationary run
        bits.writeBits(2, 0) // stationary selector 0 => 0 further players skipped
        alignToByte(bits)

        // Low-resolution section: every other slot, all stationary, none added. One stationary run
        // covers this first slot plus the remaining 2045 (the client counts the run's own slot).
        bits.writeBits(1, 0) // active? no -> stationary run
        bits.writeBits(2, STATIONARY_SELECTOR_11_BITS) // an 11-bit count follows
        bits.writeBits(11, LOW_RES_PLAYER_COUNT - 1) // 2045 further slots skipped
        alignToByte(bits)

        return bits.toByteArray()
    }

    /** Pads the bit stream with zero bits up to the next byte boundary (the client byte-aligns each section). */
    private fun alignToByte(bits: BitBuf) {
        val remainder = bits.bitPosition and 7
        if (remainder != 0) bits.writeBits(8 - remainder, 0)
    }
}
