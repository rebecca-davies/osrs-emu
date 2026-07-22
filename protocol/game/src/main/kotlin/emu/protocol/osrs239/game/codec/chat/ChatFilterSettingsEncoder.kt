package emu.protocol.osrs239.game.codec.chat

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.chat.ChatFilterSettings
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes public chat as p1Alt1 and trade chat as p1Alt3. */
object ChatFilterSettingsEncoder : CipherIndependentMessageEncoder<ChatFilterSettings> {
    override val prot: Prot = GameServerProt.CHAT_FILTER_SETTINGS
    override val messageType = ChatFilterSettings::class.java
    override fun encode(message: ChatFilterSettings): ByteArray =
        JagexBuffer.alloc(2).apply {
            writeByteAlt1(message.publicFilter)
            writeByteAlt3(message.tradeFilter)
        }.array
}
