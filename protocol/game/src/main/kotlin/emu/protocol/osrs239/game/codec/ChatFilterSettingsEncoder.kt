package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.ChatFilterSettings
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes public chat as p1Alt1 and trade chat as p1Alt3. */
object ChatFilterSettingsEncoder : MessageEncoder<ChatFilterSettings> {
    override val prot: Prot = GameServerProt.CHAT_FILTER_SETTINGS
    override val messageType = ChatFilterSettings::class.java
    override fun encode(cipher: StreamCipher, message: ChatFilterSettings): ByteArray =
        JagexBuffer.alloc(2).apply {
            writeByteAlt1(message.publicFilter)
            writeByteAlt3(message.tradeFilter)
        }.array
}
