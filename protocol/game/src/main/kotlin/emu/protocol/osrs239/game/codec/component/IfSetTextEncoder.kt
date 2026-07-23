package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.component.IfSetText
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes component text followed by a p4Alt2 combined id in byte order 1,0,3,2. */
object IfSetTextEncoder : CipherIndependentMessageEncoder<IfSetText> {
    override val prot: Prot = GameServerProt.IF_SET_TEXT
    override val messageType = IfSetText::class.java

    override fun encode(message: IfSetText): ByteArray {
        val textSize = message.text.toByteArray(CP1252).size
        return JagexBuffer.alloc(textSize + TERMINATOR_SIZE + COMBINED_ID_SIZE).apply {
            writeCString(message.text)
            writeIntAlt2((message.interfaceId shl 16) or message.componentId)
        }.array
    }

    private const val TERMINATOR_SIZE = 1
    private const val COMBINED_ID_SIZE = 4
    private val CP1252 = charset("windows-1252")
}
