package emu.protocol.osrs239.game.message.component

import emu.transport.message.OutgoingMessage

/** Replaces one component's text with a NUL-free CP-1252 string that fits its packet body. */
data class IfSetText(
    val interfaceId: Int,
    val componentId: Int,
    val text: String,
) : OutgoingMessage {
    init {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        require(componentId in UNSIGNED_SHORT) { "component id must fit an unsigned short" }
        require('\u0000' !in text) { "component text cannot contain NUL" }
        require(CP1252.newEncoder().canEncode(text)) {
            "component text must be encodable as CP-1252"
        }
        require(text.toByteArray(CP1252).size <= MAX_TEXT_BYTES) {
            "component text exceeds the variable-short packet body"
        }
    }

    private companion object {
        const val MAX_TEXT_BYTES = 0xFFFF - Int.SIZE_BYTES - 1
        val UNSIGNED_SHORT = 0..0xFFFF
        val CP1252 = charset("windows-1252")
    }
}
