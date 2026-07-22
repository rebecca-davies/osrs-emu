package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.component.CloseModal
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes the client's zero-body modal close request. */
object CloseModalDecoder : MessageDecoder<CloseModal> {
    override val prot = GameClientProt.CLOSE_MODAL

    override fun decode(buf: JagexBuffer): CloseModal {
        require(buf.readableBytes() == 0) { "CLOSE_MODAL body must be empty" }
        return CloseModal
    }
}
