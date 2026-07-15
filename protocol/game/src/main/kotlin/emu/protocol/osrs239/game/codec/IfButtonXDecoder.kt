package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.transport.codec.MessageDecoder
import emu.protocol.osrs239.game.message.IfButtonX
import emu.protocol.osrs239.game.prot.GameClientProt

/** Decodes rev-239's fixed nine-byte generic interface-button packet. */
object IfButtonXDecoder : MessageDecoder<IfButtonX> {
    override val prot = GameClientProt.IF_BUTTONX

    override fun decode(buf: JagexBuffer): IfButtonX {
        require(buf.readableBytes() == BODY_SIZE) { "IF_BUTTONX body must be $BODY_SIZE bytes" }
        val combinedId = buf.readInt()
        val sub = buf.readUShort().normalizeOptional()
        val obj = buf.readUShort().normalizeOptional()
        return IfButtonX(combinedId, sub, obj, buf.readUByte())
    }

    private fun Int.normalizeOptional(): Int = if (this == 0xFFFF) -1 else this

    private const val BODY_SIZE = 9
}
