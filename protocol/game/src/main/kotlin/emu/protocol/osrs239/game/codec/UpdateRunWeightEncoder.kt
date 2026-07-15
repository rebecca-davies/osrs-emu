package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.UpdateRunWeight
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes carried weight as an untransformed big-endian signed u16. */
object UpdateRunWeightEncoder : MessageEncoder<UpdateRunWeight> {
    override val prot: Prot = GameServerProt.UPDATE_RUN_WEIGHT
    override val messageType = UpdateRunWeight::class.java
    override fun encode(cipher: StreamCipher, message: UpdateRunWeight): ByteArray =
        JagexBuffer.alloc(2).apply { writeShort(message.weight) }.array
}
