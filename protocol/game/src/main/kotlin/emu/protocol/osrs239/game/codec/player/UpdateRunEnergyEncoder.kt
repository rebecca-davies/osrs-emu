package emu.protocol.osrs239.game.codec.player

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.player.UpdateRunEnergy
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes run energy as an untransformed big-endian u16. */
object UpdateRunEnergyEncoder : CipherIndependentMessageEncoder<UpdateRunEnergy> {
    override val prot: Prot = GameServerProt.UPDATE_RUN_ENERGY
    override val messageType = UpdateRunEnergy::class.java
    override fun encode(message: UpdateRunEnergy): ByteArray =
        JagexBuffer.alloc(2).apply { writeShort(message.energy) }.array
}
