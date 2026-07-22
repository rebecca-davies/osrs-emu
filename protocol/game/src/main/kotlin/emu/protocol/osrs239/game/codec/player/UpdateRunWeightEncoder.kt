package emu.protocol.osrs239.game.codec.player

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.player.UpdateRunWeight
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes carried weight as an untransformed big-endian signed u16. */
object UpdateRunWeightEncoder : CipherIndependentMessageEncoder<UpdateRunWeight> {
    override val prot: Prot = GameServerProt.UPDATE_RUN_WEIGHT
    override val messageType = UpdateRunWeight::class.java
    override fun encode(message: UpdateRunWeight): ByteArray =
        JagexBuffer.alloc(2).apply { writeShort(message.weight) }.array
}
