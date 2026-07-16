package emu.protocol.osrs239.game.codec.player

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.player.ResetAnims
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes the empty entity-animation reset packet. */
object ResetAnimsEncoder : MessageEncoder<ResetAnims> {
    override val prot: Prot = GameServerProt.RESET_ANIMS
    override val messageType = ResetAnims::class.java
    override fun encode(cipher: StreamCipher, message: ResetAnims): ByteArray = ByteArray(0)
}
