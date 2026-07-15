package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.MessageGame
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes a [MessageGame] body: smart-1-or-2 type, a name-present flag with an optional NUL-terminated
 * sender name, then the NUL-terminated message. The inverse of rsprox rev-239's `MessageGameDecoder`.
 */
object MessageGameEncoder : MessageEncoder<MessageGame> {
    override val prot: Prot = GameServerProt.MESSAGE_GAME
    override val messageType = MessageGame::class.java

    override fun encode(cipher: StreamCipher, message: MessageGame): ByteArray {
        val cp1252 = charset("windows-1252")
        val name = message.name
        val nameBytes = name?.toByteArray(cp1252)
        val messageBytes = message.message.toByteArray(cp1252)
        val typeSize = if (message.type < 0x80) 1 else 2
        val size = typeSize + 1 + (nameBytes?.let { it.size + 1 } ?: 0) + messageBytes.size + 1
        return JagexBuffer.alloc(size).apply {
            writeSmart1or2(message.type)
            if (nameBytes != null) {
                writeByte(1)
                writeBytes(nameBytes)
                writeByte(0)
            } else {
                writeByte(0)
            }
            writeBytes(messageBytes)
            writeByte(0)
        }.array
    }
}
