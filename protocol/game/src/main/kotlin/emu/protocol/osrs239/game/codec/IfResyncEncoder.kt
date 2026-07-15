package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.IfResync
import emu.protocol.osrs239.game.prot.GameServerProt

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
