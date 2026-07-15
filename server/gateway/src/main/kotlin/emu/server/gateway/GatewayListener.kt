package emu.server.gateway

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import java.util.concurrent.atomic.AtomicBoolean
import java.io.EOFException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/** Bound gateway socket whose accept loop owns connection routing and closure. */
class GatewayListener internal constructor(
    private val socket: ServerSocket,
    private val selector: SelectorManager,
    private val routes: GatewayRoutes,
    maxConnections: Int,
    private val classificationTimeout: Duration,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val connections = Semaphore(maxConnections)

    val localAddress: InetSocketAddress
        get() = socket.localAddress as InetSocketAddress

    suspend fun run(): Unit = coroutineScope {
        try {
            while (currentCoroutineContext().isActive) {
                val connection = socket.accept()
                if (!connections.tryAcquire()) {
                    logger.warn { "gateway connection limit reached; rejecting connection" }
                    connection.close()
                    continue
                }
                launch {
                    try {
                        val read = connection.openReadChannel()
                        val write = connection.openWriteChannel(autoFlush = false)
                        val opcode = withTimeout(classificationTimeout) { read.readByte().toInt() and 0xFF }
                        when (opcode) {
                            routes.js5Opcode -> routes.js5.handle(read, write)
                            routes.loginOpcode -> routes.login.handle(read, write)
                            else -> logger.warn { "gateway received unknown first opcode $opcode" }
                        }
                    } catch (_: TimeoutCancellationException) {
                        logger.debug { "gateway connection classification timed out" }
                    } catch (_: EOFException) {
                        logger.debug { "gateway connection closed before classification" }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Throwable) {
                        logger.error(failure) { "gateway connection failed" }
                    } finally {
                        connection.close()
                        connections.release()
                    }
                }
            }
        } finally {
            close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        socket.close()
        selector.close()
    }
}
