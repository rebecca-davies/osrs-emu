package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes an eight-bit high-resolution NPC count of zero and no additions. */
object NpcInfoEncoder : MessageEncoder<NpcInfo> {
    override val prot: Prot = GameServerProt.NPC_INFO
    override val messageType = NpcInfo::class.java
    override fun encode(cipher: StreamCipher, message: NpcInfo): ByteArray = byteArrayOf(0)
}
