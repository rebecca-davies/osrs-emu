package emu.protocol.osrs239.game.codec.npc

import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes an eight-bit high-resolution NPC count of zero and no additions. */
object NpcInfoEncoder : CipherIndependentMessageEncoder<NpcInfo> {
    override val prot: Prot = GameServerProt.NPC_INFO
    override val messageType = NpcInfo::class.java
    override fun encode(message: NpcInfo): ByteArray = byteArrayOf(0)
}
