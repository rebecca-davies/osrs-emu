package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.PlayerAppearance
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

    /**
     * Extended-info flag bit for the **appearance** block (rev-239 `PlayerInfoClient`: `APPEARANCE
     * = 0x20`). It sits in the low byte of the flag word and carries neither the `0x8`
     * (EXTENDED_SHORT) nor `0x800` (EXTENDED_MEDIUM) widening bit, so an appearance-only update
     * writes the flag word as a single byte.
     */
    private const val APPEARANCE_FLAG = 0x20

    /** Signed-byte sentinel `-1` (`0xFF`) for the appearance's skull/overhead icon "none" fields. */
    private const val ICON_NONE = 0xFF

    override fun encode(cipher: StreamCipher, message: PlayerInfo): ByteArray {
        val bits = BitBuf()
        val appearance = message.appearance

        // High-resolution section: the sole high-res player is the local avatar. Its byte layout is
        // identical whether the client reads it in the "active-last-cycle" (cycle 0) or
        // "inactive-last-cycle" (cycle 1+) high-res section, because both sections run the same
        // active/getHighResolutionPlayerPosition/readStationary logic and byte-align identically
        // (rev-239 PlayerInfoClient.decodeBitCodes) — so this encoding is cycle-independent.
        if (appearance == null) {
            // No extended info: a zero-length stationary run (selector 0 => skip 0). Sets the local
            // player IDLE with no avatar model — the minimal per-tick heartbeat.
            bits.writeBits(1, 0) // active? no -> stationary run
            bits.writeBits(2, 0) // stationary selector 0 => 0 further players skipped
        } else {
            // Active update carrying extended info: active=1, has-extended-info=1, movement
            // opcode 0 (idle). getHighResolutionPlayerPosition treats opcode 0 + extended-info as a
            // clean IDLE (NOT the `localIndex == idx` throw that active-with-no-extended-info hits),
            // and queues this index for the extended-info pass below.
            bits.writeBits(1, 1) // active? yes
            bits.writeBits(1, 1) // has extended info? yes
            bits.writeBits(2, 0) // movement opcode 0 => idle (no walk/run)
        }
        alignToByte(bits)

        // Low-resolution section: every other slot, all stationary, none added. One stationary run
        // covers this first slot plus the remaining 2045 (the client counts the run's own slot).
        bits.writeBits(1, 0) // active? no -> stationary run
        bits.writeBits(2, STATIONARY_SELECTOR_11_BITS) // an 11-bit count follows
        bits.writeBits(11, LOW_RES_PLAYER_COUNT - 1) // 2045 further slots skipped
        alignToByte(bits)

        val gpi = bits.toByteArray()
        if (appearance == null) return gpi

        // Extended-info pass (byte-aligned, after all bit sections): one entry, for the local
        // player queued above. Flag word = APPEARANCE only (single byte); then the appearance block
        // length as g1Alt3 (`value + 128`, matching the client's `g1Alt3` read and our other
        // p1Alt3 packets); then the appearance sub-buffer itself.
        val block = buildAppearanceBlock(appearance)
        val out = JagexBuffer.alloc(gpi.size + 2 + block.size)
        out.writeBytes(gpi)
        out.writeByte(APPEARANCE_FLAG)
        out.writeByte((block.size + 128) and 0xFF)
        out.writeBytes(block)
        return out.array
    }

    /**
     * Serializes the rev-239 appearance sub-buffer exactly as the client's `decodeAppearance`
     * reads it: gender, skull icon, overhead icon, 12 equipment/identikit slots, 12 interface
     * identikit slots, 5 body colours, 7 render animations, the display name (cp1252 C-string),
     * combat level, skill level, hidden flag, customisation flag (0 = none), three name-extra
     * C-strings, and the text gender. Values come from [PlayerAppearance]; the fields it does not
     * model (icons, interface kit, hidden, customisation, name extras) are the inert defaults a
     * plain avatar uses.
     */
    private fun buildAppearanceBlock(a: PlayerAppearance): ByteArray {
        // 3 header + 12 equip (<=2B each) + 12 interface + 5 colours + 14 anim + name + 6 fixed +
        // 3 empty C-strings — comfortably within this bound.
        val buf = JagexBuffer.alloc(64 + a.name.toByteArray(Charsets.ISO_8859_1).size)
        buf.writeByte(a.gender)
        buf.writeByte(ICON_NONE) // skull icon: none
        buf.writeByte(ICON_NONE) // overhead prayer icon: none
        for (slot in a.equipment) {
            if (slot == 0) {
                buf.writeByte(0) // empty slot
            } else {
                buf.writeByte(slot ushr 8) // flag byte (1 = identikit, 2 = worn item)
                buf.writeByte(slot and 0xFF) // model/kit id low byte
            }
        }
        repeat(PlayerAppearance.EQUIPMENT_SLOT_COUNT) { buf.writeByte(0) } // interface identikit: none
        for (colour in a.colors) buf.writeByte(colour)
        for (anim in a.animations) buf.writeShort(anim)
        buf.writeCString(a.name)
        buf.writeByte(a.combatLevel)
        buf.writeShort(a.skillLevel)
        buf.writeByte(0) // hidden = false
        buf.writeShort(0) // customisation flag = none
        buf.writeCString("") // name prefix
        buf.writeCString("") // name suffix
        buf.writeCString("") // after-combat-level text
        buf.writeByte(a.gender) // text gender
        return buf.array.copyOf(buf.pos)
    }

    /** Pads the bit stream with zero bits up to the next byte boundary (the client byte-aligns each section). */
    private fun alignToByte(bits: BitBuf) {
        val remainder = bits.bitPosition and 7
        if (remainder != 0) bits.writeBits(8 - remainder, 0)
    }
}
