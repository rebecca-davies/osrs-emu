package emu.protocol.osrs239.game.codec.cycle

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.cycle.PacketGroupStart
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the signed big-endian u16 packet-group byte length read by rev 239. */
object PacketGroupStartEncoder : CipherIndependentMessageEncoder<PacketGroupStart> {
    override val prot: Prot = GameServerProt.PACKET_GROUP_START
    override val messageType = PacketGroupStart::class.java
    override fun encode(message: PacketGroupStart): ByteArray =
        JagexBuffer.alloc(2).apply { writeShort(message.length) }.array
}
