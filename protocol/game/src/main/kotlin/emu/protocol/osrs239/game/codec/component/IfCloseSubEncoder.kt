package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.component.IfCloseSub
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes the revision-239 destination combined id as a big-endian integer. */
object IfCloseSubEncoder : MessageEncoder<IfCloseSub> {
    override val prot: Prot = GameServerProt.IF_CLOSE_SUB
    override val messageType = IfCloseSub::class.java

    override fun encode(cipher: StreamCipher, message: IfCloseSub): ByteArray =
        JagexBuffer.alloc(4).apply {
            writeInt((message.interfaceId shl 16) or message.componentId)
        }.array
}
