package emu.protocol.osrs239.game.codec.npc

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.npc.OpNpc2
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes revision 239's four-byte second NPC operation body. */
object OpNpc2Decoder : MessageDecoder<OpNpc2> {
    override val prot = GameClientProt.OPNPC2

    override fun decode(buf: JagexBuffer): OpNpc2 {
        require(buf.readableBytes() == BODY_SIZE) { "OPNPC2 body must be $BODY_SIZE bytes" }
        return OpNpc2(
            index = buf.readUShortAlt3(),
            subOption = buf.readUByteAlt2(),
            controlKey = buf.readUByteAlt3() == CONTROL_KEY,
        )
    }

    private const val BODY_SIZE = 4
    private const val CONTROL_KEY = 1
}
