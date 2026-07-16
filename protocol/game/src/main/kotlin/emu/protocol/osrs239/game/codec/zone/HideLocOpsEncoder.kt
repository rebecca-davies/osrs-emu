package emu.protocol.osrs239.game.codec.zone

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.zone.HideLocOps
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes location-option visibility as a Jagex boolean byte. */
object HideLocOpsEncoder : MessageEncoder<HideLocOps> {
    override val prot: Prot = GameServerProt.HIDE_LOC_OPS
    override val messageType = HideLocOps::class.java
    override fun encode(cipher: StreamCipher, message: HideLocOps): ByteArray = byteArrayOf(if (message.hidden) 1 else 0)
}
