package emu.server.login

import emu.server.login.wire.performLoginInit

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

// Login init replies with a zero status byte followed by the big-endian server session key.
class LoginFlowTest {
    @Test fun `opcode 14 replies status 0 then the 8-byte server session key`() = runBlocking {
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        // Login init is a one-byte route discriminator with no payload. The test therefore drives
        // the login handshake directly; framed login blocks are covered separately.
        val serverJob = launch {
            val conn = server.accept()
            val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
            when (r.readByte().toInt() and 0xFF) {
                14 -> performLoginInit(w, sessionKey = 0x0102030405060708L)
                else -> {}
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)
        cw.writeByte(14) // opcode 14: single byte, no payload

        val reply = ByteArray(9)
        cr.readFully(reply)
        assertEquals(0, reply[0].toInt())
        assertEquals((1..8).toList(), reply.drop(1).map { it.toInt() and 0xFF })

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
