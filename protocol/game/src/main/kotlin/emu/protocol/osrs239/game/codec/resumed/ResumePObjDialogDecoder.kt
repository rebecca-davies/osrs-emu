package emu.protocol.osrs239.game.codec.resumed

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.resumed.ResumePObjDialog
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.MessageDecoder

/** Decodes rev-239's raw unsigned-short `RESUME_P_OBJDIALOG` body. */
object ResumePObjDialogDecoder : MessageDecoder<ResumePObjDialog> {
    override val prot = GameClientProt.RESUME_P_OBJDIALOG

    override fun decode(buf: JagexBuffer): ResumePObjDialog {
        require(buf.readableBytes() == BODY_SIZE) { "RESUME_P_OBJDIALOG body must be $BODY_SIZE bytes" }
        return ResumePObjDialog(buf.readUShort())
    }

    private const val BODY_SIZE = Short.SIZE_BYTES
}
