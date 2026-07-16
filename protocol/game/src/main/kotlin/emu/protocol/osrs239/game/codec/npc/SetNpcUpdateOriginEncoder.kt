package emu.protocol.osrs239.game.codec.npc

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.npc.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes two untransformed u8 origins; the pipeline applies opcode ISAAC. */
object SetNpcUpdateOriginEncoder : MessageEncoder<SetNpcUpdateOrigin> {
    override val prot: Prot = GameServerProt.SET_NPC_UPDATE_ORIGIN
    override val messageType = SetNpcUpdateOrigin::class.java

    override fun encode(cipher: StreamCipher, message: SetNpcUpdateOrigin): ByteArray =
        byteArrayOf((message.originX and 0xFF).toByte(), (message.originZ and 0xFF).toByte())
}
