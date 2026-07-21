package emu.protocol.osrs239.game.codec.client

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.client.ClientCheat
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes the bounded NUL-terminated developer-console line. */
object ClientCheatDecoder : MessageDecoder<ClientCheat> {
    override val prot = GameClientProt.CLIENT_CHEAT

    override fun decode(buf: JagexBuffer): ClientCheat {
        require(buf.readableBytes() in 1..ClientCheat.MAX_INPUT_LENGTH + 1) {
            "CLIENT_CHEAT body must contain 1..${ClientCheat.MAX_INPUT_LENGTH + 1} bytes"
        }
        require(buf.array.last() == 0.toByte()) { "CLIENT_CHEAT input must be NUL-terminated" }
        val input = buf.readCString()
        require(buf.readableBytes() == 0) { "CLIENT_CHEAT contains bytes after its terminator" }
        return ClientCheat(input)
    }
}
