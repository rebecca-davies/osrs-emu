package emu.protocol.osrs239.game.codec.audio

import emu.protocol.osrs239.game.message.audio.AmbienceStop
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the untransformed fade boolean; opcode 138 exercises the two-byte smart header. */
object AmbienceStopEncoder : CipherIndependentMessageEncoder<AmbienceStop> {
    override val prot: Prot = GameServerProt.AMBIENCE_STOP
    override val messageType = AmbienceStop::class.java
    override fun encode(message: AmbienceStop): ByteArray = byteArrayOf(if (message.fade) 1 else 0)
}
