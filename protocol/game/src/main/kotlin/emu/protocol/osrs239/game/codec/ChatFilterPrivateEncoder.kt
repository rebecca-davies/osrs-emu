package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.protocol.osrs239.game.message.ChatFilterPrivate
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the fixed one-byte private-chat filter update. */
object ChatFilterPrivateEncoder : MessageEncoder<ChatFilterPrivate> {
    override val prot = GameServerProt.CHAT_FILTER_SETTINGS_PRIVATE
    override val messageType = ChatFilterPrivate::class.java
    override fun encode(cipher: StreamCipher, message: ChatFilterPrivate): ByteArray =
        byteArrayOf(message.privateFilter.toByte())
}
