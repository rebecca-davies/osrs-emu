package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.VarpReset
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the empty varp-table reset packet. */
object VarpResetEncoder : MessageEncoder<VarpReset> {
    override val prot: Prot = GameServerProt.VARP_RESET
    override val messageType = VarpReset::class.java
    override fun encode(cipher: StreamCipher, message: VarpReset): ByteArray = ByteArray(0)
}
