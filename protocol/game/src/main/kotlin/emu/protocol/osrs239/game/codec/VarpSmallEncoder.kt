package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.VarpSmall
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes a signed p1Alt1 value followed by a p2Alt3 varp id. */
object VarpSmallEncoder : MessageEncoder<VarpSmall> {
    override val prot: Prot = GameServerProt.VARP_SMALL
    override val messageType = VarpSmall::class.java
    override fun encode(cipher: StreamCipher, message: VarpSmall): ByteArray =
        JagexBuffer.alloc(3).apply {
            writeByteAlt1(message.value)
            writeShortAlt3(message.id)
        }.array
}
