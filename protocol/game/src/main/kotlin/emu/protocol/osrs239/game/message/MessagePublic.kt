package emu.protocol.osrs239.game.message

import emu.netcore.message.IncomingMessage

/** Rev-239 public-chat envelope. [encodedText] remains cache-Huffman encoded at this wire layer. */
data class MessagePublic(
    val type: Int,
    val colour: Int,
    val effect: Int,
    val encodedText: ByteArray,
    val pattern: ByteArray? = null,
) : IncomingMessage {
    override fun equals(other: Any?): Boolean =
        other is MessagePublic &&
            type == other.type && colour == other.colour && effect == other.effect &&
            encodedText.contentEquals(other.encodedText) && pattern.contentEquals(other.pattern)

    override fun hashCode(): Int =
        listOf(type, colour, effect, encodedText.contentHashCode(), pattern?.contentHashCode()).hashCode()
}
