package emu.server.gateway

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
                        route?.handle(read, write)
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
}
