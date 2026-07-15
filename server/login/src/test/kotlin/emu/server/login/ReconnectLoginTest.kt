package emu.server.login

import emu.server.login.auth.LoginAuthenticator

import emu.server.login.wire.loginSuccessTrailer
import emu.server.login.wire.performLoginBlock
import emu.protocol.osrs239.login.codec.LoginBlockParser

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import emu.server.session.AuthenticationCompletion
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

/** Pins the rev-239 success response contract for fresh and reconnect login blocks. */
class ReconnectLoginTest {

    private fun loginBlockPayload(
        keyPair: RsaKeyPair,
        seeds: IntArray,
        serverKey: Long,
        reconnect: Boolean,
    ): ByteArray {
        val password = "testpass"
        val authTokenLength = if (reconnect) 16 else 5
        val plaintext = JagexBuffer.alloc(1 + 16 + 8 + authTokenLength + 1 + password.length + 1).apply {
            writeByte(1)
            for (s in seeds) writeInt(s)
            writeLong(serverKey)
            if (reconnect) {
                repeat(4) { writeInt(0) }
            } else {
                writeByte(2)
                writeInt(0)
            }
            writeByte(0)
            writeCString(password)
        }.array
        val cipherBytes = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
        val tail = encryptedUsernameTail(TEST_LOGIN_NAME, seeds)
        return JagexBuffer.alloc(LoginBlockParser.CLEARTEXT_HEADER_SIZE + 2 + cipherBytes.size + tail.size).apply {
            writeBytes(ByteArray(LoginBlockParser.CLEARTEXT_HEADER_SIZE))
            writeShort(cipherBytes.size)
            writeBytes(cipherBytes)
            writeBytes(tail)
        }.array
    }

    /** Runs [performLoginBlock] over in-memory channels and returns every byte it wrote. */
    private fun loginResponseBytes(keyPair: RsaKeyPair, reconnect: Boolean): ByteArray = runBlocking {
        val serverKey = 0x123456789ABCL
        val payload = loginBlockPayload(keyPair, intArrayOf(1, 2, 3, 4), serverKey, reconnect)
        val read = ByteChannel()
        read.writeFully(byteArrayOf((payload.size ushr 8).toByte(), payload.size.toByte()))
        read.writeFully(payload)
        read.flush()
        val write = ByteChannel()
        val authenticated = performLoginBlock(
            read,
            write,
            serverKey,
            keyPair,
            reconnect = reconnect,
            authenticate = ::acceptTestLogin,
        )
        assertNotNull(authenticated, "login block should be accepted")
        LoginServer(keyPair, LoginAuthenticator(::acceptTestLogin)).use { loginServer ->
            loginServer.complete(write, authenticated, AuthenticationCompletion.Accepted(playerIndex = 1))
        }
        write.close()
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(256)
        while (true) {
            val n = write.readAvailable(tmp, 0, tmp.size)
            if (n == -1) break
            out.write(tmp, 0, n)
        }
        out.toByteArray()
    }

    @Test fun `fresh op16 completion writes response 2 and the account trailer`() {
        val keyPair = Rsa.generateKeyPair(1024)
        val response = loginResponseBytes(keyPair, reconnect = false)
        assertContentEquals(byteArrayOf(2), response.copyOfRange(0, 1))
        assertContentEquals(loginSuccessTrailer(TEST_PRINCIPAL.privilege, 1), response.copyOfRange(1, response.size))
    }

    @Test fun `op18 reconnect gets response 2 and NOTHING else`() {
        val keyPair = Rsa.generateKeyPair(1024)
        assertContentEquals(byteArrayOf(2), loginResponseBytes(keyPair, reconnect = true))
    }
}
