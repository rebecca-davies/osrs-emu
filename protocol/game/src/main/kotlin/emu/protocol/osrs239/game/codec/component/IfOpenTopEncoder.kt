package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.component.IfOpenTop
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the top-level interface id as p2Alt2. */
object IfOpenTopEncoder : CipherIndependentMessageEncoder<IfOpenTop> {
    override val prot: Prot = GameServerProt.IF_OPEN_TOP
    override val messageType = IfOpenTop::class.java
    override fun encode(message: IfOpenTop): ByteArray =
        JagexBuffer.alloc(2).apply { writeShortAlt2(message.interfaceId) }.array
}
