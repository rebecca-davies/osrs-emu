package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.CamReset
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the empty camera-reset packet. */
object CamResetEncoder : MessageEncoder<CamReset> {
    override val prot: Prot = GameServerProt.CAM_RESET
    override val messageType = CamReset::class.java
    override fun encode(cipher: StreamCipher, message: CamReset): ByteArray = ByteArray(0)
}
