package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

/**
 * Pins the response-2 wire contract for both login opcodes, from the decompiled rev-239 client's
 * response dispatch (`client.java`, the `n == 2` branch):
 *  - **op16 fresh login** (`cd.az` -> `cd.am`): the client reads one length byte + the 37-byte
 *    login-info block — so the server must send [LOGIN_SUCCESS_TRAILER] after the response code.
 *  - **op18 reconnect** (`ol.cl` set -> state `cd.aj`): the client reads NOTHING after the response
 *    code — its `cd.aj` handler consumes no bytes and drops straight into the game-packet loop. A
 *    server that sends the trailer anyway desyncs the whole session by 38 bytes (observed live:
 *    the client mis-framed our REBUILD_NORMAL as opcode 28/71 and threw `dy.ae: 773 4614`).
 */
class ReconnectLoginTest {

    private fun loadRealOrSkip(): RsaKeyPair? {
        val file = File("../server-rsa.properties")
        if (!file.isFile) {
            println("SKIP: no server-rsa.properties — run :tools:client-patch first")
            return null
        }
        return ServerRsaKeyFile.load(file)
    }

    private fun loginBlockPayload(keyPair: RsaKeyPair, seeds: IntArray, serverKey: Long): ByteArray {
        val password = "testpass"
        val plaintext = JagexBuffer.alloc(1 + 16 + 8 + 1 + 1 + password.length + 1).apply {
            writeByte(1)
            for (s in seeds) writeInt(s)
            writeLong(serverKey)
            writeByte(0)
            writeByte(0)
            writeCString(password)
        }.array
        val cipherBytes = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
        return JagexBuffer.alloc(LoginBlockParser.CLEARTEXT_HEADER_SIZE + 2 + cipherBytes.size + 8).apply {
            writeBytes(ByteArray(LoginBlockParser.CLEARTEXT_HEADER_SIZE))
            writeShort(cipherBytes.size)
            writeBytes(cipherBytes)
            writeBytes(ByteArray(8))
        }.array
    }

    /** Runs [performLoginBlock] over in-memory channels and returns every byte it wrote. */
    private fun loginResponseBytes(keyPair: RsaKeyPair, reconnect: Boolean): ByteArray = runBlocking {
        val serverKey = 0x123456789ABCL
        val payload = loginBlockPayload(keyPair, intArrayOf(1, 2, 3, 4), serverKey)
        val read = ByteChannel()
        read.writeFully(byteArrayOf((payload.size ushr 8).toByte(), payload.size.toByte()))
        read.writeFully(payload)
        read.flush()
        val write = ByteChannel()
        val ciphers = performLoginBlock(read, write, serverKey, keyPair, reconnect = reconnect)
        assertNotNull(ciphers, "login block should be accepted")
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

    @Test fun `fresh op16 login gets response 2 plus the 38-byte login-info trailer`() {
        val keyPair = loadRealOrSkip() ?: return
        assertContentEquals(byteArrayOf(2) + LOGIN_SUCCESS_TRAILER, loginResponseBytes(keyPair, reconnect = false))
    }

    @Test fun `op18 reconnect gets response 2 and NOTHING else`() {
        val keyPair = loadRealOrSkip() ?: return
        assertContentEquals(byteArrayOf(2), loginResponseBytes(keyPair, reconnect = true))
    }
}
