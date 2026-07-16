package emu.protocol.osrs239.game.codec.chat

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.chat.ChatFilterPrivate
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder

/** Encodes the fixed one-byte private-chat filter update. */
object ChatFilterPrivateEncoder : MessageEncoder<ChatFilterPrivate> {
    override val prot = GameServerProt.CHAT_FILTER_SETTINGS_PRIVATE
    override val messageType = ChatFilterPrivate::class.java
    override fun encode(cipher: StreamCipher, message: ChatFilterPrivate): ByteArray =
        byteArrayOf(message.privateFilter.toByte())
}
