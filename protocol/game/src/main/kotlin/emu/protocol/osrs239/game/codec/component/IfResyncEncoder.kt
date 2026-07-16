package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.component.IfResync
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes a canonical interface tree snapshot using untransformed combined ids. */
object IfResyncEncoder : MessageEncoder<IfResync> {
    override val prot: Prot = GameServerProt.IF_RESYNC
    override val messageType = IfResync::class.java
    override fun encode(cipher: StreamCipher, message: IfResync): ByteArray =
        JagexBuffer.alloc(4 + message.subInterfaces.size * 7).apply {
            writeShort(message.topLevelInterface)
            writeShort(message.subInterfaces.size)
            for (sub in message.subInterfaces) {
                writeInt((sub.destinationInterfaceId shl 16) or sub.destinationComponentId)
                writeShort(sub.interfaceId)
                writeByte(sub.type)
            }
        }.array
}
