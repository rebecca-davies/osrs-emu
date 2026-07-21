package emu.protocol.osrs239.game.codec.client

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.client.NoTimeout
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes rev-239's zero-body connection keepalive. */
object NoTimeoutDecoder : MessageDecoder<NoTimeout> {
    override val prot = GameClientProt.NO_TIMEOUT

    override fun decode(buf: JagexBuffer): NoTimeout {
        require(buf.readableBytes() == 0) { "NO_TIMEOUT body must be empty" }
        return NoTimeout
    }
}
