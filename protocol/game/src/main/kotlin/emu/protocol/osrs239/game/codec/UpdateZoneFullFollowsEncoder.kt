package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.UpdateZoneFullFollows
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes zone Z as p1Alt3, zone X as p1Alt2, then the level byte. */
object UpdateZoneFullFollowsEncoder : MessageEncoder<UpdateZoneFullFollows> {
    override val prot: Prot = GameServerProt.UPDATE_ZONE_FULL_FOLLOWS
    override val messageType = UpdateZoneFullFollows::class.java
    override fun encode(cipher: StreamCipher, message: UpdateZoneFullFollows): ByteArray =
        JagexBuffer.alloc(3).apply {
            writeByteAlt3(message.zoneZ)
            writeByteAlt2(message.zoneX)
            writeByte(message.level)
        }.array
}
