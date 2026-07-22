package emu.protocol.osrs239.game.codec.npc

import emu.protocol.osrs239.game.message.npc.HideNpcOps
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes NPC-option visibility as a Jagex boolean byte. */
object HideNpcOpsEncoder : CipherIndependentMessageEncoder<HideNpcOps> {
    override val prot: Prot = GameServerProt.HIDE_NPC_OPS
    override val messageType = HideNpcOps::class.java
    override fun encode(message: HideNpcOps): ByteArray = byteArrayOf(if (message.hidden) 1 else 0)
}
