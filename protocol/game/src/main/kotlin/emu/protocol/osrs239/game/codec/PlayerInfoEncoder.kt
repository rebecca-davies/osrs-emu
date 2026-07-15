package emu.protocol.osrs239.game.codec

import emu.buffer.BitBuf
import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerMovement
import emu.protocol.osrs239.game.message.PlayerPublicChat
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [PlayerInfo] (opcode 28) as the rev-239 OSRS "GPI" bit stream for the current case: only
 * the local player is present and nobody else is added.
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
 * A stationary local player is encoded as an inactive stationary run of length zero. The client
 * rejects an active local update with movement opcode zero and no extended information.
 *
 * **Extended-info / appearance:** when present, the appearance flag and serialized sub-buffer are
 * appended after the four bit sections. Steady-state cycles omit it and retain the initial model.
 *
 * The pipeline applies the opcode's ISAAC adjustment; this encoder does not consume [cipher].
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
    private const val CHAT_FLAG = 0x100
    private const val EXTENDED_SHORT_FLAG = 0x8

    /** Cached and per-movement speed flags from rev-239's extended-info decoder. */
    private const val MOVE_SPEED_FLAG = 0x400
    private const val TEMP_MOVE_SPEED_FLAG = 0x1000

    /** Signed-byte sentinel `-1` (`0xFF`) for the appearance's skull/overhead icon "none" fields. */
    private const val ICON_NONE = 0xFF

    override fun encode(cipher: StreamCipher, message: PlayerInfo): ByteArray {
        val bits = BitBuf()
        val appearance = message.appearance
        val movement = message.movement
        val publicChat = message.publicChat
        val hasExtendedInfo =
            appearance != null || publicChat != null ||
                message.moveSpeed != null || message.temporaryMoveSpeed != null

        // High-resolution section: the sole high-res player is the local avatar. Its byte layout is
        // identical whether the client reads it in the "active-last-cycle" (cycle 0) or
        // "inactive-last-cycle" (cycle 1+) high-res section, because both sections run the same
        // active/getHighResolutionPlayerPosition/readStationary logic and byte-align identically
        // (rev-239 PlayerInfoClient.decodeBitCodes) — so this encoding is cycle-independent.
        if (!hasExtendedInfo && movement == null) {
            // No extended info: a zero-length stationary run (selector 0 => skip 0). Sets the local
            // player IDLE with no avatar model — the minimal per-tick heartbeat.
            bits.writeBits(1, 0) // active? no -> stationary run
            bits.writeBits(2, 0) // stationary selector 0 => 0 further players skipped
        } else {
            // Active movement and/or extended-info update. An opcode-0 idle update is legal only
            // with extended info; the branch above keeps an unextended idle player stationary.
            bits.writeBits(1, 1) // active? yes
            bits.writeBits(1, if (hasExtendedInfo) 1 else 0)
            when (movement) {
                null -> bits.writeBits(2, 0)
                is PlayerMovement.Walk -> {
                    bits.writeBits(2, 1)
                    bits.writeBits(3, walkDirection(movement.deltaX, movement.deltaY))
                }
                is PlayerMovement.Run -> {
                    bits.writeBits(2, 2)
                    bits.writeBits(4, runDirection(movement.deltaX, movement.deltaY))
                }
            }
        }
        alignToByte(bits)

        // Low-resolution section: every other slot, all stationary, none added. One stationary run
        // covers this first slot plus the remaining 2045 (the client counts the run's own slot).
        bits.writeBits(1, 0) // active? no -> stationary run
        bits.writeBits(2, STATIONARY_SELECTOR_11_BITS) // an 11-bit count follows
        bits.writeBits(11, LOW_RES_PLAYER_COUNT - 1) // 2045 further slots skipped
        alignToByte(bits)

        val gpi = bits.toByteArray()
        if (!hasExtendedInfo) return gpi

        var flags = 0
        if (appearance != null) flags = flags or APPEARANCE_FLAG
        if (publicChat != null) flags = flags or CHAT_FLAG
        if (message.moveSpeed != null) flags = flags or MOVE_SPEED_FLAG
        if (message.temporaryMoveSpeed != null) flags = flags or TEMP_MOVE_SPEED_FLAG
        if (flags ushr 8 != 0) flags = flags or EXTENDED_SHORT_FLAG

        // Extended-info pass: low flag byte first and a high byte when 0x8 is set. Rev 239 decodes
        // cached speed, chat, temporary speed, then appearance, irrespective of numeric flag order.
        val appearanceBlock = appearance?.let(::buildAppearanceBlock)
        val chatBlock = publicChat?.let(::buildChatBlock)
        val flagBytes = if (flags and EXTENDED_SHORT_FLAG != 0) 2 else 1
        val out = JagexBuffer.alloc(
            gpi.size + flagBytes +
                (if (message.moveSpeed != null) 1 else 0) +
                (chatBlock?.size ?: 0) +
                (if (message.temporaryMoveSpeed != null) 1 else 0) +
                (appearanceBlock?.let { 1 + it.size } ?: 0),
        )
        out.writeBytes(gpi)
        out.writeByte(flags)
        if (flags and EXTENDED_SHORT_FLAG != 0) out.writeByte(flags ushr 8)
        message.moveSpeed?.let(out::writeByteAlt2)
        if (chatBlock != null) out.writeBytes(chatBlock)
        message.temporaryMoveSpeed?.let(out::writeByte)
        if (appearanceBlock != null) {
            out.writeByteAlt3(appearanceBlock.size)
            for (byte in appearanceBlock) out.writeByte(byte.toInt() + 128)
        }
        return out.array
    }

    /** Serializes rev-239's CHAT extended-info block in the client's positional decode order. */
    private fun buildChatBlock(chat: PlayerPublicChat): ByteArray {
        val patternSize = chat.pattern?.size ?: 0
        val out = JagexBuffer.alloc(5 + chat.encodedText.size + patternSize)
        out.writeShortAlt2((chat.colour shl 8) or chat.effect)
        out.writeByteAlt2(chat.modIcon)
        out.writeByteAlt1(if (chat.autotyper) 1 else 0)
        out.writeByteAlt2(chat.encodedText.size)
        for (index in chat.encodedText.indices.reversed()) out.writeByte(chat.encodedText[index].toInt())
        chat.pattern?.forEach { out.writeByteAlt2(it.toInt()) }
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

    /** Rev-239 opcode-1 direction table, ordered south-west through north-east. */
    private fun walkDirection(deltaX: Int, deltaY: Int): Int =
        when (deltaX to deltaY) {
            -1 to -1 -> 0
            0 to -1 -> 1
            1 to -1 -> 2
            -1 to 0 -> 3
            1 to 0 -> 4
            -1 to 1 -> 5
            0 to 1 -> 6
            1 to 1 -> 7
            else -> error("invalid walk delta: $deltaX,$deltaY")
        }

    /** Rev-239 opcode-2 table for the outer ring of the 5x5 two-step delta grid. */
    private fun runDirection(deltaX: Int, deltaY: Int): Int =
        when (deltaX to deltaY) {
            -2 to -2 -> 0
            -1 to -2 -> 1
            0 to -2 -> 2
            1 to -2 -> 3
            2 to -2 -> 4
            -2 to -1 -> 5
            2 to -1 -> 6
            -2 to 0 -> 7
            2 to 0 -> 8
            -2 to 1 -> 9
            2 to 1 -> 10
            -2 to 2 -> 11
            -1 to 2 -> 12
            0 to 2 -> 13
            1 to 2 -> 14
            2 to 2 -> 15
            else -> error("invalid run delta: $deltaX,$deltaY")
        }
}
