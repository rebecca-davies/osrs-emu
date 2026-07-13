package emu.netcore.rpc

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class FrameCodecTest {
    @Test fun `frame roundtrips over a loopback socket`() = runBlocking {
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val addr = server.localAddress as InetSocketAddress

        val payload = ByteArray(5000) { (it % 251).toByte() }

        launch {
            val conn = server.accept()
            val out = conn.openWriteChannel(autoFlush = false)
            out.writeFrame(payload)
            conn.close()
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", addr.port))
        val received = client.openReadChannel().readFrame()
        assertContentEquals(payload, received)

        client.close(); server.close(); selector.close()
    }

    @Test fun `oversized frame is rejected`() = runBlocking {
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val addr = server.localAddress as InetSocketAddress

        launch {
            val conn = server.accept()
            val out = conn.openWriteChannel(autoFlush = false)
            out.writeFrame(ByteArray(2000))
            conn.close()
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", addr.port))
        assertFailsWith<FrameTooLargeException> {
            client.openReadChannel().readFrame(maxLen = 1000)
        }
        client.close(); server.close(); selector.close()
    }
}
