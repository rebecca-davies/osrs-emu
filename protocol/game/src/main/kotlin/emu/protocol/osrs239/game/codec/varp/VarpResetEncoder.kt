package emu.protocol.osrs239.game.codec.varp

import emu.protocol.osrs239.game.message.varp.VarpReset
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the empty varp-table reset packet. */
object VarpResetEncoder : CipherIndependentMessageEncoder<VarpReset> {
    override val prot: Prot = GameServerProt.VARP_RESET
    override val messageType = VarpReset::class.java
    override fun encode(message: VarpReset): ByteArray = ByteArray(0)
}
