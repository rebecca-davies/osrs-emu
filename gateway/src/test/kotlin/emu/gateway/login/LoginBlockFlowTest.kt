package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

// Proves the op-14 -> op-16 login handshake end-to-end over a real loopback socket, mirroring
// Main.kt's dispatch: opcode 14 replies the server session key (and remembers it for this
// connection), then a synthesized op-16 "new login" block — built with the SAME RSA keypair the
// gateway reads from server-rsa.properties — is decrypted, its echoed server key checked, ISAAC
// ciphers built, and response code 2 (+ trailer) sent back. See
// docs/superpowers/research/2026-07-14-rev239-login-facts.md §1-§4 and LoginBlockParser's doc for
// the exact plaintext layout and the header-offset/auth-byte assumptions this test locks in.
class LoginBlockFlowTest {

    // Same fixture convention as Js5RealCacheTest: use the real, gitignored keypair
    // tools:client-patch generates if present (so this exercises the exact file Main.kt reads),
    // otherwise skip rather than fail a fresh checkout that hasn't run that tool yet.
    private fun loadRealOrSkip(): RsaKeyPair? {
        val file = File("../server-rsa.properties")
        if (!file.isFile) {
            println("SKIP: no server-rsa.properties — run :tools:client-patch:run first")
            return null
        }
        return ServerRsaKeyFile.load(file)
    }

    private fun rsaPlaintext(seeds: IntArray, serverKey: Long, password: String): ByteArray {
        val buf = JagexBuffer.alloc(1 + 16 + 8 + 1 + 1 + password.length + 1)
        buf.writeByte(1) // magic
        for (s in seeds) buf.writeInt(s)
        buf.writeLong(serverKey)
        buf.writeByte(0) // auth-method byte (assumed 0 = no authenticator; see LoginBlockParser doc)
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

    @Test fun `op 14 then a synthesized op 16 login block reaches response code 2`() = runBlocking {
        val keyPair = loadRealOrSkip() ?: return@runBlocking

        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        // Mirrors Main.kt's op-14 -> op-16/18 dispatch directly (ProtocolStage isn't involved pre-login).
        val serverJob = launch {
            val conn = server.accept()
            val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
            when (r.readByte().toInt() and 0xFF) {
                14 -> {
                    val serverKey = performLoginInit(w)
                    when (r.readByte().toInt() and 0xFF) {
                        16, 18 -> performLoginBlock(
                            r,
                            w,
                            serverKey,
                            keyPair,
                            authenticate = ::acceptTestLogin,
                        )
                        else -> {}
                    }
                }
                else -> {}
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)

        cw.writeByte(14) // opcode 14: single byte, no payload
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
        val trailer = ByteArray(LOGIN_SUCCESS_TRAILER.size)
        cr.readFully(trailer)

        assertEquals(2, responseCode)
        assertEquals(LOGIN_SUCCESS_TRAILER.toList(), trailer.toList())

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
