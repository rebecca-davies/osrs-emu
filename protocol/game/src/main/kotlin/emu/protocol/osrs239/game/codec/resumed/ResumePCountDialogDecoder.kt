package emu.protocol.osrs239.game.codec.resumed

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.resumed.ResumePCountDialog
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes rev-239's four-byte `RESUME_P_COUNTDIALOG` body. */
object ResumePCountDialogDecoder : MessageDecoder<ResumePCountDialog> {
    override val prot = GameClientProt.RESUME_P_COUNTDIALOG

    override fun decode(buf: JagexBuffer): ResumePCountDialog {
        require(buf.readableBytes() == BODY_SIZE) {
            "RESUME_P_COUNTDIALOG body must be $BODY_SIZE bytes"
        }
        return ResumePCountDialog(buf.readInt())
    }

    private const val BODY_SIZE = Int.SIZE_BYTES
}
