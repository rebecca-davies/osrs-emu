package emu.protocol.osrs239.game.codec.varp

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.varp.VarpLarge
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes a p2Alt2 varp id followed by a little-endian p4Alt1 value. */
object VarpLargeEncoder : CipherIndependentMessageEncoder<VarpLarge> {
    override val prot: Prot = GameServerProt.VARP_LARGE
    override val messageType = VarpLarge::class.java
    override fun encode(message: VarpLarge): ByteArray =
        JagexBuffer.alloc(6).apply {
            writeShortAlt2(message.id)
            writeIntAlt1(message.value)
        }.array
}
