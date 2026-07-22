package emu.protocol.osrs239.game.codec.chat

import emu.protocol.osrs239.game.message.chat.ChatFilterPrivate
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder

/** Encodes the fixed one-byte private-chat filter update. */
object ChatFilterPrivateEncoder : CipherIndependentMessageEncoder<ChatFilterPrivate> {
    override val prot = GameServerProt.CHAT_FILTER_SETTINGS_PRIVATE
    override val messageType = ChatFilterPrivate::class.java
    override fun encode(message: ChatFilterPrivate): ByteArray =
        byteArrayOf(message.privateFilter.toByte())
}
