package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.AmbienceStop
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the untransformed fade boolean; opcode 138 exercises the two-byte smart header. */
object AmbienceStopEncoder : MessageEncoder<AmbienceStop> {
    override val prot: Prot = GameServerProt.AMBIENCE_STOP
    override val messageType = AmbienceStop::class.java
    override fun encode(cipher: StreamCipher, message: AmbienceStop): ByteArray = byteArrayOf(if (message.fade) 1 else 0)
}
