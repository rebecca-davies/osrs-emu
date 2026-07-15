package emu.server.gateway

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.openReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeByte
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class GatewayServiceTest {
    @Test
    fun `first opcode selects the configured service route`() = runBlocking {
        val routed = CompletableDeferred<String>()
        val listener =
            GatewayService(
                GatewayConfig(bindHost = "127.0.0.1", port = 0),
                GatewayRoutes(
                    js5Opcode = 15,
                    loginOpcode = 14,
                    js5 = GatewayRouteHandler { _, _ -> routed.complete("js5") },
                    login = GatewayRouteHandler { _, _ -> routed.complete("login") },
                ),
            ).bind()
        val listenerJob = launch { listener.run() }
        val selector = SelectorManager(Dispatchers.IO)
        val client = aSocket(selector).tcp().connect(listener.localAddress)
        client.openWriteChannel(autoFlush = true).writeByte(14)

        assertEquals("login", withTimeout(2_000) { routed.await() })

        client.close()
        selector.close()
        listenerJob.cancel()
        listener.close()
        listenerJob.cancelAndJoin()
    }

    @Test
    fun `classification timeout closes an idle connection and releases its slot`() = runBlocking {
        val routed = CompletableDeferred<Unit>()
        val listener =
            GatewayService(
                GatewayConfig(
                    bindHost = "127.0.0.1",
                    port = 0,
                    maxConnections = 1,
                    classificationTimeout = 50.milliseconds,
                ),
                GatewayRoutes(
                    js5Opcode = 15,
                    loginOpcode = 14,
                    js5 = GatewayRouteHandler { _, _ -> },
                    login = GatewayRouteHandler { _, _ -> routed.complete(Unit) },
                ),
            ).bind()
        val listenerJob = launch { listener.run() }
        val selector = SelectorManager(Dispatchers.IO)
        val idle = aSocket(selector).tcp().connect(listener.localAddress)
        val idleRead = idle.openReadChannel()

        delay(150)
        assertFailsWith<Throwable> { withTimeout(1_000) { idleRead.readByte() } }

        val replacement = aSocket(selector).tcp().connect(listener.localAddress)
        replacement.openWriteChannel(autoFlush = true).writeByte(14)
        withTimeout(1_000) { routed.await() }

        idle.close()
        replacement.close()
        selector.close()
        listenerJob.cancel()
        listener.close()
        listenerJob.cancelAndJoin()
    }
}
