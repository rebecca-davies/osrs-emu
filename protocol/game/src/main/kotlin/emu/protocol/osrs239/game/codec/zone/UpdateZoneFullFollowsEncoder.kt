package emu.protocol.osrs239.game.codec.zone

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.zone.UpdateZoneFullFollows
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes zone Z as p1Alt3, zone X as p1Alt2, then the level byte. */
object UpdateZoneFullFollowsEncoder : CipherIndependentMessageEncoder<UpdateZoneFullFollows> {
    override val prot: Prot = GameServerProt.UPDATE_ZONE_FULL_FOLLOWS
    override val messageType = UpdateZoneFullFollows::class.java
    override fun encode(message: UpdateZoneFullFollows): ByteArray =
        JagexBuffer.alloc(3).apply {
            writeByteAlt3(message.zoneZ)
            writeByteAlt2(message.zoneX)
            writeByte(message.level)
        }.array
}
