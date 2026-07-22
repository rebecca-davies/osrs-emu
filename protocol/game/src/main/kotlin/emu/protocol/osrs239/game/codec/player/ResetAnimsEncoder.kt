package emu.protocol.osrs239.game.codec.player

import emu.protocol.osrs239.game.message.player.ResetAnims
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the empty entity-animation reset packet. */
object ResetAnimsEncoder : CipherIndependentMessageEncoder<ResetAnims> {
    override val prot: Prot = GameServerProt.RESET_ANIMS
    override val messageType = ResetAnims::class.java
    override fun encode(message: ResetAnims): ByteArray = ByteArray(0)
}
