package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.IfOpenTop
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the top-level interface id as p2Alt2. */
object IfOpenTopEncoder : MessageEncoder<IfOpenTop> {
    override val prot: Prot = GameServerProt.IF_OPEN_TOP
    override val messageType = IfOpenTop::class.java
    override fun encode(cipher: StreamCipher, message: IfOpenTop): ByteArray =
        JagexBuffer.alloc(2).apply { writeShortAlt2(message.interfaceId) }.array
}
