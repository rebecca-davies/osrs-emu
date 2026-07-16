package emu.protocol.osrs239.game.codec.npc

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes an eight-bit high-resolution NPC count of zero and no additions. */
object NpcInfoEncoder : MessageEncoder<NpcInfo> {
    override val prot: Prot = GameServerProt.NPC_INFO
    override val messageType = NpcInfo::class.java
    override fun encode(cipher: StreamCipher, message: NpcInfo): ByteArray = byteArrayOf(0)
}
