package emu.protocol.osrs239.game.codec.chat

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.chat.MessagePublic
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Frames the modern public-chat header and leaves cache-Huffman interpretation to the gateway. */
object MessagePublicDecoder : MessageDecoder<MessagePublic> {
    override val prot = GameClientProt.MESSAGE_PUBLIC

    override fun decode(buf: JagexBuffer): MessagePublic {
        require(buf.readableBytes() >= MIN_BODY_SIZE) { "MESSAGE_PUBLIC body is too short" }
        val type = buf.readUByte()
        val colour = buf.readUByte()
        val effect = buf.readUByte()
        val pattern =
            if (colour in PATTERN_COLOURS) {
                val length = colour - 12
                require(buf.readableBytes() > length) { "MESSAGE_PUBLIC pattern consumes text payload" }
                buf.readBytes(length)
            } else null
        val clanTrailerSize = if (type == CLAN_MAIN_CHANNEL_TYPE) 1 else 0
        require(buf.readableBytes() > clanTrailerSize) { "MESSAGE_PUBLIC is missing Huffman text" }
        val encoded = buf.readBytes(buf.readableBytes() - clanTrailerSize)
        if (clanTrailerSize != 0) buf.readUByte()
        return MessagePublic(type, colour, effect, encoded, pattern)
    }

    private const val MIN_BODY_SIZE = 4
    private const val CLAN_MAIN_CHANNEL_TYPE = 3
    private val PATTERN_COLOURS = 13..20
}
