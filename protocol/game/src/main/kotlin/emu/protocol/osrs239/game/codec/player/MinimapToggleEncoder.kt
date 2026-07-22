package emu.protocol.osrs239.game.codec.player

import emu.protocol.osrs239.game.message.player.MinimapToggle
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the untransformed minimap state byte. */
object MinimapToggleEncoder : CipherIndependentMessageEncoder<MinimapToggle> {
    override val prot: Prot = GameServerProt.MINIMAP_TOGGLE
    override val messageType = MinimapToggle::class.java
    override fun encode(message: MinimapToggle): ByteArray = byteArrayOf(message.state.toByte())
}
