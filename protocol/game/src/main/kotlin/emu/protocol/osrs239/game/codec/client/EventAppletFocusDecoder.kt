package emu.protocol.osrs239.game.codec.client

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.client.EventAppletFocus
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes rev-239's one-byte applet focus transition. */
object EventAppletFocusDecoder : MessageDecoder<EventAppletFocus> {
    override val prot = GameClientProt.EVENT_APPLET_FOCUS

    override fun decode(buf: JagexBuffer): EventAppletFocus {
        require(buf.readableBytes() == BODY_SIZE) {
            "EVENT_APPLET_FOCUS body must be one byte"
        }
        return EventAppletFocus(focused = buf.readUByte() == FOCUSED)
    }

    private const val BODY_SIZE = 1
    private const val FOCUSED = 1
}
