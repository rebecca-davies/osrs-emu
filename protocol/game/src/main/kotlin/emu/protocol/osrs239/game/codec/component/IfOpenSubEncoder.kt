package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes interface id p2Alt3, destination combined id p4Alt3, then subinterface type. */
object IfOpenSubEncoder : MessageEncoder<IfOpenSub> {
    override val prot: Prot = GameServerProt.IF_OPEN_SUB
    override val messageType = IfOpenSub::class.java
    override fun encode(cipher: StreamCipher, message: IfOpenSub): ByteArray =
        JagexBuffer.alloc(7).apply {
            writeShortAlt3(message.interfaceId)
            writeIntAlt3((message.destinationInterfaceId shl 16) or message.destinationComponentId)
            writeByte(message.type)
        }.array
}
