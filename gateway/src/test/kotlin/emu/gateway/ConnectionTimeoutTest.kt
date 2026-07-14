package emu.gateway

import emu.cache.store.FlatFileStore
import emu.netcore.codec.CodecRepositoryBuilder
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

// Covers CLAUDE.md §10 (read/idle timeouts at the internet-facing edge): a client that opens the
// socket and never sends a byte must not hold handleConnection's coroutine + fd forever. Uses a
// tiny injected handshakeTimeout (see Main.kt's handleConnection) rather than sleeping for the real
// 15s HANDSHAKE_TIMEOUT, so this stays fast and non-flaky.
class ConnectionTimeoutTest {
    @Test fun `a client that sends nothing is disconnected within the handshake timeout`() = runBlocking {
        val store = FlatFileStore(Files.createTempDirectory("gw-timeout").toFile())
        val codecs = CodecRepositoryBuilder().build()

        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        val serverJob = launch {
            val conn = server.accept()
            handleConnection(conn, store, codecs, codecs, rsaKeyPair = null, handshakeTimeout = 100.milliseconds)
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel()

        // The client sends nothing at all. Once the tiny handshake timeout elapses, handleConnection
        // must close the socket (its finally { conn.close() }) rather than hang — the client observes
        // EOF/failure reading, not an indefinite block.
        assertFailsWith<Throwable> {
            withTimeout(2000) { cr.readByte() }
        }

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }

    @Test fun `a client that stalls after opcode 14 (mid-login) is disconnected within the handshake timeout`() = runBlocking {
        val store = FlatFileStore(Files.createTempDirectory("gw-timeout").toFile())
        val codecs = CodecRepositoryBuilder().build()

        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        val serverJob = launch {
            val conn = server.accept()
            handleConnection(conn, store, codecs, codecs, rsaKeyPair = null, handshakeTimeout = 100.milliseconds)
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)
        cw.writeByte(14) // opcode 14 (LoginProt.INIT): server replies the session key, then waits
        // for the next opcode (16/18) — this client never sends it, simulating a stalled login.
        val initReply = ByteArray(9)
        cr.readFully(initReply)
        assertEquals(0, initReply[0].toInt())

        assertFailsWith<Throwable> {
            withTimeout(2000) { cr.readByte() }
        }

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
