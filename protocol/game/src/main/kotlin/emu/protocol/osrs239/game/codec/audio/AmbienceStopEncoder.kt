package emu.protocol.osrs239.game.codec.audio

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.audio.AmbienceStop
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes the untransformed fade boolean; opcode 138 exercises the two-byte smart header. */
object AmbienceStopEncoder : MessageEncoder<AmbienceStop> {
    override val prot: Prot = GameServerProt.AMBIENCE_STOP
    override val messageType = AmbienceStop::class.java
    override fun encode(cipher: StreamCipher, message: AmbienceStop): ByteArray = byteArrayOf(if (message.fade) 1 else 0)
}
