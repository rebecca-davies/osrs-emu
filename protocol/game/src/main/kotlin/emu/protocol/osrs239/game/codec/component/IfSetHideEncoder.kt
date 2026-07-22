package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.component.IfSetHide
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes a component id and hidden flag with rev-239's alternate transforms. */
object IfSetHideEncoder : CipherIndependentMessageEncoder<IfSetHide> {
    override val prot: Prot = GameServerProt.IF_SET_HIDE
    override val messageType = IfSetHide::class.java

    override fun encode(message: IfSetHide): ByteArray =
        JagexBuffer.alloc(5).apply {
            writeIntAlt3((message.interfaceId shl 16) or message.componentId)
            writeByteAlt1(if (message.hidden) 1 else 0)
        }.array
}
