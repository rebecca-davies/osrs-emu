package emu.protocol.osrs239.game.codec.varp

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.varp.VarpSmall
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes a signed p1Alt1 value followed by a p2Alt3 varp id. */
object VarpSmallEncoder : CipherIndependentMessageEncoder<VarpSmall> {
    override val prot: Prot = GameServerProt.VARP_SMALL
    override val messageType = VarpSmall::class.java
    override fun encode(message: VarpSmall): ByteArray =
        JagexBuffer.alloc(3).apply {
            writeByteAlt1(message.value)
            writeShortAlt3(message.id)
        }.array
}
