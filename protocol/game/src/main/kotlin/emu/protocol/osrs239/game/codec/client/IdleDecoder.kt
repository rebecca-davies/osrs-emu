package emu.protocol.osrs239.game.codec.client

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.client.Idle
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes the client's zero-body idle logout request. */
object IdleDecoder : MessageDecoder<Idle> {
    override val prot = GameClientProt.IDLE

    override fun decode(buf: JagexBuffer): Idle {
        require(buf.readableBytes() == 0) { "IDLE body must be empty" }
        return Idle
    }
}
