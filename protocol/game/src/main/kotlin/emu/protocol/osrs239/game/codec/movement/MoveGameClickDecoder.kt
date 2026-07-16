package emu.protocol.osrs239.game.codec.movement

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.movement.MoveGameClick
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes rev-239's five-byte `MOVE_GAMECLICK` body. */
object MoveGameClickDecoder : MessageDecoder<MoveGameClick> {
    override val prot = GameClientProt.MOVE_GAMECLICK

    override fun decode(buf: JagexBuffer): MoveGameClick {
        require(buf.readableBytes() == BODY_SIZE) { "MOVE_GAMECLICK body must be $BODY_SIZE bytes" }
        val x = buf.readUByte() or (buf.readUByte() shl 8)
        val z = buf.readUByte() or (buf.readUByte() shl 8)
        val keyCombination = (128 - buf.readUByte()) and 0xFF
        return MoveGameClick(x, z, keyCombination)
    }

    private const val BODY_SIZE = 5
}
