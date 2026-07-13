package emu.protocol.osrs239.login

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot

/**
 * The client reads exactly one response byte after sending its login block (`uo2.af()`); code 2 =
 * success (rev239-login-facts.md §6). `LoginHandler` sends this once the login block is validated.
 * Same registry-only-opcode pattern as [ServerSessionKeyEncoder].
 */
object LoginResponseEncoder : MessageEncoder<LoginResponse> {
    override val prot: Prot = LoginProt.OUTGOING

    override fun encode(cipher: StreamCipher, message: LoginResponse): ByteArray =
        byteArrayOf(message.code.toByte())
}
