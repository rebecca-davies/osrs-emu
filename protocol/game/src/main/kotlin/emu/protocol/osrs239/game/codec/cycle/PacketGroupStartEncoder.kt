package emu.protocol.osrs239.game.codec.cycle

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.cycle.PacketGroupStart
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes the signed big-endian u16 packet-group byte length read by rev 239. */
object PacketGroupStartEncoder : MessageEncoder<PacketGroupStart> {
    override val prot: Prot = GameServerProt.PACKET_GROUP_START
    override val messageType = PacketGroupStart::class.java
    override fun encode(cipher: StreamCipher, message: PacketGroupStart): ByteArray =
        JagexBuffer.alloc(2).apply { writeShort(message.length) }.array
}
