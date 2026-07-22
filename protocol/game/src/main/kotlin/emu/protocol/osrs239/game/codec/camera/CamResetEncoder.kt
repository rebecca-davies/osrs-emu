package emu.protocol.osrs239.game.codec.camera

import emu.protocol.osrs239.game.message.camera.CamReset
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the empty camera-reset packet. */
object CamResetEncoder : CipherIndependentMessageEncoder<CamReset> {
    override val prot: Prot = GameServerProt.CAM_RESET
    override val messageType = CamReset::class.java
    override fun encode(message: CamReset): ByteArray = ByteArray(0)
}
