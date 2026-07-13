package emu.protocol.osrs239.login

import emu.buffer.JagexBuffer
import emu.netcore.codec.MessageDecoder
import emu.netcore.prot.Prot

// Opcode 14 carries no payload (LoginProt.INIT size = 0); decode is handed an empty buffer.
class LoginInitDecoder : MessageDecoder<LoginInit> {
    override val prot: Prot = LoginProt.INIT
    override fun decode(buf: JagexBuffer): LoginInit = LoginInit
}
