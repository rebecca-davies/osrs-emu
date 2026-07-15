package emu.protocol.osrs239.login.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.login.message.LoginResponse
import emu.protocol.osrs239.login.prot.LoginProt

/**
 * The client reads exactly one response byte after sending its login block (`uo2.af()`); code 2 =
 * success (rev239-login-facts.md §6). `LoginHandler` sends this once the login block is validated.
 * Same registry-only-opcode pattern as [ServerSessionKeyEncoder].
 */
object LoginResponseEncoder : MessageEncoder<LoginResponse> {
    override val prot: Prot = LoginProt.OUTGOING
    override val messageType = LoginResponse::class.java

    override fun encode(cipher: StreamCipher, message: LoginResponse): ByteArray =
        byteArrayOf(message.code.toByte())
}
