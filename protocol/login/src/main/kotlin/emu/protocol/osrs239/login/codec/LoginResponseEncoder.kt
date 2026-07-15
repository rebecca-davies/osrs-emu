package emu.protocol.osrs239.login.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.login.message.LoginResponse
import emu.protocol.osrs239.login.prot.LoginProt

/** Encodes the single response byte written during login processing. */
object LoginResponseEncoder : MessageEncoder<LoginResponse> {
    override val prot: Prot = LoginProt.OUTGOING
    override val messageType = LoginResponse::class.java

    override fun encode(cipher: StreamCipher, message: LoginResponse): ByteArray =
        byteArrayOf(message.code.toByte())
}
