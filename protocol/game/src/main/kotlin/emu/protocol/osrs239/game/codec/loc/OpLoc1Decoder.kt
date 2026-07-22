package emu.protocol.osrs239.game.codec.loc

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.loc.OpLoc1
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes the rev-239 eight-byte `OPLOC1` body. */
object OpLoc1Decoder : MessageDecoder<OpLoc1> {
    override val prot = GameClientProt.OPLOC1

    override fun decode(buf: JagexBuffer): OpLoc1 {
        require(buf.readableBytes() == BODY_SIZE) { "OPLOC1 body must be $BODY_SIZE bytes" }
        val z = (buf.readUByte() shl 8) or ((buf.readUByte() - 128) and 0xFF)
        val x = buf.readUByte() or (buf.readUByte() shl 8)
        val keyCombination = (buf.readUByte() - 128) and 0xFF
        val subOption = (buf.readUByte() - 128) and 0xFF
        return OpLoc1(buf.readUShort(), x, z, subOption, keyCombination)
    }

    private const val BODY_SIZE = 8
}
