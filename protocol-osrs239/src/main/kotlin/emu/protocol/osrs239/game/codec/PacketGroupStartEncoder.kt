package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.PacketGroupStart
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the signed big-endian u16 packet-group byte length read by rev 239. */
object PacketGroupStartEncoder : MessageEncoder<PacketGroupStart> {
    override val prot: Prot = GameServerProt.PACKET_GROUP_START
    override val messageType = PacketGroupStart::class.java
    override fun encode(cipher: StreamCipher, message: PacketGroupStart): ByteArray =
        JagexBuffer.alloc(2).apply { writeShort(message.length) }.array
}
