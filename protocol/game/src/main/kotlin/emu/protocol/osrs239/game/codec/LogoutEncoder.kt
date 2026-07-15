package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.Logout
import emu.protocol.osrs239.game.prot.GameServerProt

object LogoutEncoder : MessageEncoder<Logout> {
    override val prot: Prot = GameServerProt.LOGOUT
    override val messageType = Logout::class.java
    override fun encode(cipher: StreamCipher, message: Logout): ByteArray = ByteArray(0)
}
