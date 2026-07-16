package emu.protocol.osrs239.game.codec.zone

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.zone.HideObjOps
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes ground-object-option visibility as a Jagex boolean byte. */
object HideObjOpsEncoder : MessageEncoder<HideObjOps> {
    override val prot: Prot = GameServerProt.HIDE_OBJ_OPS
    override val messageType = HideObjOps::class.java
    override fun encode(cipher: StreamCipher, message: HideObjOps): ByteArray = byteArrayOf(if (message.hidden) 1 else 0)
}
