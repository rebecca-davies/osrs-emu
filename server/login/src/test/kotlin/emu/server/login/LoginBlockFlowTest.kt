package emu.server.login

import emu.protocol.osrs239.login.codec.LoginBlockParser

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import emu.server.session.AuthenticationCompletion
import emu.server.login.auth.LoginAuthenticator
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginBlockFlowTest {

    private fun rsaPlaintext(seeds: IntArray, serverKey: Long, password: String): ByteArray {
        val buf = JagexBuffer.alloc(1 + 16 + 8 + 1 + 4 + 1 + password.length + 1)
        buf.writeByte(1) // magic
        for (s in seeds) buf.writeInt(s)
        buf.writeLong(serverKey)
        buf.writeByte(2) // auth-method wire value for client.dm case 0
        buf.writeInt(0) // fixed four-byte auth payload (reserved for case 0)
        buf.writeByte(0) // string-type marker byte
        buf.writeCString(password)
        return buf.array
    }

    private fun loginBlockPayload(keyPair: RsaKeyPair, seeds: IntArray, serverKey: Long, password: String): ByteArray {
        val plaintext = rsaPlaintext(seeds, serverKey, password)
        val cipherBytes = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
        val header = ByteArray(LoginBlockParser.CLEARTEXT_HEADER_SIZE) // contents irrelevant to the parser
        val usernameTail = encryptedUsernameTail(TEST_LOGIN_NAME, seeds)
        val out = JagexBuffer.alloc(header.size + 2 + cipherBytes.size + usernameTail.size)
        out.writeBytes(header)
        out.writeShort(cipherBytes.size)
        out.writeBytes(cipherBytes)
        out.writeBytes(usernameTail)
        return out.array
    }

    @Test fun `fresh login completes only after admission supplies a player index`() = runBlocking {
        val keyPair = Rsa.generateKeyPair(1024)
        val loginServer = LoginServer(keyPair, LoginAuthenticator(::acceptTestLogin))

        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        val serverJob = launch {
            val conn = server.accept()
            val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
            val authenticated = loginServer.authenticate(r, w)
            if (authenticated != null) {
                loginServer.complete(w, authenticated, AuthenticationCompletion.Accepted(playerIndex = 1))
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)

        val initReply = ByteArray(9)
        cr.readFully(initReply)
        assertEquals(0, initReply[0].toInt()) // status ok

        var serverKey = 0L
        for (i in 1..8) serverKey = (serverKey shl 8) or (initReply[i].toLong() and 0xFF)

        val seeds = intArrayOf(11, 22, 33, 44)
        val payload = loginBlockPayload(keyPair, seeds, serverKey, password = "hunter2")

        cw.writeByte(16) // new-login
        cw.writeByte((payload.size ushr 8).toByte()) // u16 length, big-endian
        cw.writeByte((payload.size and 0xFF).toByte())
        cw.writeFully(payload)

        val responseCode = cr.readByte().toInt() and 0xFF
        assertEquals(2, responseCode)

        serverJob.cancel()
        client.close(); server.close(); selector.close(); loginServer.close()
    }
}
