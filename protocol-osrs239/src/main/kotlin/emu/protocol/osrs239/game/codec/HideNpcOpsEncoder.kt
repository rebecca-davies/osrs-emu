package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.HideNpcOps
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes NPC-option visibility as a Jagex boolean byte. */
object HideNpcOpsEncoder : MessageEncoder<HideNpcOps> {
    override val prot: Prot = GameServerProt.HIDE_NPC_OPS
    override val messageType = HideNpcOps::class.java
    override fun encode(cipher: StreamCipher, message: HideNpcOps): ByteArray = byteArrayOf(if (message.hidden) 1 else 0)
}
