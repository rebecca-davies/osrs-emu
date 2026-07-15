package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.transport.codec.MessageDecoder
import emu.protocol.osrs239.game.message.SetChatFilterSettings
import emu.protocol.osrs239.game.prot.GameClientProt

/** Decodes the fixed public/private/trade filter triplet. */
object SetChatFilterSettingsDecoder : MessageDecoder<SetChatFilterSettings> {
    override val prot = GameClientProt.SET_CHAT_FILTER_SETTINGS

    override fun decode(buf: JagexBuffer): SetChatFilterSettings {
        require(buf.readableBytes() == 3) { "SET_CHATFILTERSETTINGS body must be three bytes" }
        return SetChatFilterSettings(buf.readUByte(), buf.readUByte(), buf.readUByte())
    }
}
