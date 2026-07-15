package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.HideObjOps
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes ground-object-option visibility as a Jagex boolean byte. */
object HideObjOpsEncoder : MessageEncoder<HideObjOps> {
    override val prot: Prot = GameServerProt.HIDE_OBJ_OPS
    override val messageType = HideObjOps::class.java
    override fun encode(cipher: StreamCipher, message: HideObjOps): ByteArray = byteArrayOf(if (message.hidden) 1 else 0)
}
