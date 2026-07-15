package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.UpdateInvFull
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes a full inventory replacement, including rev-239 count/id transforms. */
object UpdateInvFullEncoder : MessageEncoder<UpdateInvFull> {
    override val prot: Prot = GameServerProt.UPDATE_INV_FULL
    override val messageType = UpdateInvFull::class.java
    override fun encode(cipher: StreamCipher, message: UpdateInvFull): ByteArray {
        val extraCounts = message.objects.count { it.count >= 255 } * 4
        return JagexBuffer.alloc(8 + message.objects.size * 3 + extraCounts).apply {
            writeInt((message.interfaceId shl 16) or message.componentId)
            writeShort(message.inventoryId)
            writeShort(message.objects.size)
            for (obj in message.objects) {
                if (obj.count < 255) {
                    writeByteAlt3(obj.count)
                } else {
                    writeByteAlt3(255)
                    writeIntAlt3(obj.count)
                }
                writeShortAlt1(obj.id + 1)
            }
        }.array
    }
}
