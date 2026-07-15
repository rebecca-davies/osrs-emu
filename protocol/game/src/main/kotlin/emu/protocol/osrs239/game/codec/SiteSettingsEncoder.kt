package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.SiteSettings
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes site settings as a CP-1252, NUL-terminated Jagex string. */
object SiteSettingsEncoder : MessageEncoder<SiteSettings> {
    override val prot: Prot = GameServerProt.SITE_SETTINGS
    override val messageType = SiteSettings::class.java
    override fun encode(cipher: StreamCipher, message: SiteSettings): ByteArray {
        val size = message.settings.toByteArray(charset("windows-1252")).size + 1
        return JagexBuffer.alloc(size).apply { writeCString(message.settings) }.array
    }
}
