package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.component.IfCloseSub
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the revision-239 destination combined id as a big-endian integer. */
object IfCloseSubEncoder : CipherIndependentMessageEncoder<IfCloseSub> {
    override val prot: Prot = GameServerProt.IF_CLOSE_SUB
    override val messageType = IfCloseSub::class.java

    override fun encode(message: IfCloseSub): ByteArray =
        JagexBuffer.alloc(4).apply {
            writeInt((message.interfaceId shl 16) or message.componentId)
        }.array
}
