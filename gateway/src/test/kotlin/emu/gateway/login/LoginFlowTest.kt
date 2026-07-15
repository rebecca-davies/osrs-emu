package emu.gateway.login

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

// Proves the opcode-14 exchange over a real loopback socket, mirroring how Main.kt dispatches:
// read the first opcode byte, and on 14 (LoginProt.INIT, no payload) reply with the server session
// key as [1 status byte == 0][8-byte server session key (big-endian long)].
class LoginFlowTest {
    @Test fun `opcode 14 replies status 0 then the 8-byte server session key`() = runBlocking {
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        // ProtocolStage isn't involved here — opcode 14 has no payload and no follow-up in this
        // stage (opcodes 16/18 arrive later, in Task 6), so this mirrors Main.kt's raw dispatch
        // directly rather than driving a full stage loop.
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
