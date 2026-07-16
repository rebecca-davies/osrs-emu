package emu.protocol.osrs239.game.codec.playerinfo

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfoUpdate

/** Encodes one rev-239 player extended-info flag word and its ordered blocks. */
internal object PlayerInfoExtendedEncoder {
    fun encode(update: PlayerInfoUpdate): ByteArray {
        var flags = 0
        if (update.appearance != null) flags = flags or APPEARANCE_FLAG
        if (update.publicChat != null) flags = flags or CHAT_FLAG
        if (update.moveSpeed != null) flags = flags or MOVE_SPEED_FLAG
        if (update.temporaryMoveSpeed != null) flags = flags or TEMP_MOVE_SPEED_FLAG
        if (flags ushr 8 != 0) flags = flags or EXTENDED_SHORT_FLAG

        val appearance = update.appearance?.let(::buildAppearanceBlock)
        val chat = update.publicChat?.let(::buildChatBlock)
        val flagBytes = if (flags and EXTENDED_SHORT_FLAG != 0) 2 else 1
        val out =
            JagexBuffer.alloc(
                flagBytes +
                    (if (update.moveSpeed != null) 1 else 0) +
                    (chat?.size ?: 0) +
                    (if (update.temporaryMoveSpeed != null) 1 else 0) +
                    (appearance?.let { 1 + it.size } ?: 0),
            )
        out.writeByte(flags)
        if (flags and EXTENDED_SHORT_FLAG != 0) out.writeByte(flags ushr 8)
        update.moveSpeed?.let(out::writeByteAlt2)
        if (chat != null) out.writeBytes(chat)
        update.temporaryMoveSpeed?.let(out::writeByte)
        if (appearance != null) {
            out.writeByteAlt3(appearance.size)
            for (byte in appearance) out.writeByte(byte.toInt() + 128)
        }
        return out.array
    }

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

    private fun buildAppearanceBlock(appearance: PlayerAppearance): ByteArray {
        val nameSize = appearance.name.toByteArray(CP1252).size
        val equipmentExtraBytes = appearance.body.equipment.count { it != 0 }
        val out = JagexBuffer.alloc(APPEARANCE_BASE_SIZE + nameSize + equipmentExtraBytes)
        out.writeByte(appearance.gender)
        out.writeByte(appearance.skullIcon)
        out.writeByte(appearance.prayerIcon)
        for (slot in appearance.body.equipment) {
            if (slot == 0) {
                out.writeByte(0)
            } else {
                out.writeByte(slot ushr 8)
                out.writeByte(slot)
            }
        }
        repeat(PlayerAppearance.EQUIPMENT_SLOT_COUNT) { out.writeByte(0) }
        appearance.body.colors.forEach(out::writeByte)
        appearance.body.animations.forEach(out::writeShort)
        out.writeCString(appearance.name)
        out.writeByte(appearance.combatLevel)
        out.writeShort(appearance.skillLevel)
        out.writeByte(0)
        out.writeShort(0)
        repeat(3) { out.writeCString("") }
        out.writeByte(appearance.gender)
        check(out.pos == out.array.size) { "appearance size calculation did not consume its buffer" }
        require(out.pos <= 0xFF) { "appearance block must fit its unsigned-byte length" }
        return out.array
    }

    private const val APPEARANCE_FLAG = 0x20
    private const val CHAT_FLAG = 0x100
    private const val EXTENDED_SHORT_FLAG = 0x8
    private const val MOVE_SPEED_FLAG = 0x400
    private const val TEMP_MOVE_SPEED_FLAG = 0x1000
    private const val APPEARANCE_BASE_SIZE = 57
    private val CP1252 = charset("windows-1252")
}
