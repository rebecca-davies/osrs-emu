package emu.server.gateway

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import java.io.EOFException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout

/** Bound gateway socket whose accept loop owns connection routing and closure. */
class GatewayListener internal constructor(
    private val socket: ServerSocket,
    private val selector: SelectorManager,
    private val routes: GatewayRoutes,
    maxPendingClassifications: Int,
    private val classificationTimeout: Duration,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val classifications = Semaphore(maxPendingClassifications)

    val localAddress: InetSocketAddress
        get() = socket.localAddress as InetSocketAddress

    suspend fun run(): Unit = coroutineScope {
        try {
            while (currentCoroutineContext().isActive) {
                val connection = socket.accept()
                if (!classifications.tryAcquire()) {
                    connection.close()
                    continue
                }
                launch {
                    var classifying = true
                    try {
                        val read = connection.openReadChannel()
                        val write = connection.openWriteChannel(autoFlush = false)
                        val opcode = withTimeout(classificationTimeout) { read.readByte().toInt() and 0xFF }
                        val route =
                            when (opcode) {
                                routes.js5Opcode -> routes.js5
                                routes.loginOpcode -> routes.login
                                else -> null
                            }
                        classifications.release()
                        classifying = false
                        route?.serve(read, write)
                    } catch (_: TimeoutCancellationException) {
                    } catch (_: EOFException) {
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        // One malformed or reset connection cannot affect the accept loop.
                    } finally {
                        connection.close()
                        if (classifying) classifications.release()
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

    companion object {
        /** Binds the shared JS5/login endpoint and returns its accept-loop owner. */
        suspend fun bind(config: GatewayConfig, routes: GatewayRoutes): GatewayListener {
            val selector = SelectorManager(Dispatchers.IO)
            return try {
                val socket =
                    aSocket(selector)
                        .tcp()
                        .bind(InetSocketAddress(config.bindHost, config.port))
                GatewayListener(
                    socket,
                    selector,
                    routes,
                    config.maxPendingClassifications,
                    config.classificationTimeout,
                )
            } catch (failure: Throwable) {
                selector.close()
                throw failure
            }
        }
    }
}
