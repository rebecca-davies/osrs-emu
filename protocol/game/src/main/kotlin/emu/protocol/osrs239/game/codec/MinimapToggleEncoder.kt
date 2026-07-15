package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.MinimapToggle
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the untransformed minimap state byte. */
object MinimapToggleEncoder : MessageEncoder<MinimapToggle> {
    override val prot: Prot = GameServerProt.MINIMAP_TOGGLE
    override val messageType = MinimapToggle::class.java
    override fun encode(cipher: StreamCipher, message: MinimapToggle): ByteArray = byteArrayOf(message.state.toByte())
}
