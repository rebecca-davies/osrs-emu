package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.VarpLarge
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes a p2Alt2 varp id followed by a little-endian p4Alt1 value. */
object VarpLargeEncoder : MessageEncoder<VarpLarge> {
    override val prot: Prot = GameServerProt.VARP_LARGE
    override val messageType = VarpLarge::class.java
    override fun encode(cipher: StreamCipher, message: VarpLarge): ByteArray =
        JagexBuffer.alloc(6).apply {
            writeShortAlt2(message.id)
            writeIntAlt1(message.value)
        }.array
}
