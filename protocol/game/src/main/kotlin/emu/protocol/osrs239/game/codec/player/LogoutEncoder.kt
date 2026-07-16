package emu.protocol.osrs239.game.codec.player

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.player.Logout
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes the empty rev-239 logout packet body. */
object LogoutEncoder : MessageEncoder<Logout> {
    override val prot: Prot = GameServerProt.LOGOUT
    override val messageType = Logout::class.java
    override fun encode(cipher: StreamCipher, message: Logout): ByteArray = ByteArray(0)
}
