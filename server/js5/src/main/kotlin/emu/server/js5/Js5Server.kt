package emu.server.js5

import emu.crypto.XorStreamCipher
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.js5.handler.Js5RequestHandler
import emu.server.js5.wire.installJs5Handlers
import emu.server.js5.wire.performHandshake
import emu.transport.codec.CodecRepository
import emu.transport.pipeline.ProtocolStage
import emu.transport.pipeline.handler.HandlerRepositoryBuilder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import java.io.EOFException
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns JS5 handshakes, cache requests, connection limits, and worker execution. */
class Js5Server(
    private val codecs: CodecRepository,
    private val requests: Js5RequestHandler,
    private val config: Js5ExecutionConfig = Js5ExecutionConfig(),
    private val dispatcher: ExecutorCoroutineDispatcher = js5Dispatcher(config.workerThreads),
) : Js5Service {
    private val sessions = Semaphore(config.maxConcurrentSessions)

    override suspend fun serve(read: ByteReadChannel, write: ByteWriteChannel) {
        if (!sessions.tryAcquire()) return
        try {
            try {
                withContext(dispatcher) {
                    if (!withTimeout(config.handshakeTimeout) { performHandshake(read, write) }) return@withContext
                    val cipher = XorStreamCipher()
                    val handlers = HandlerRepositoryBuilder().installJs5Handlers(requests, cipher).build()
                    ProtocolStage(
                        codecs,
                        handlers,
                        cipher,
                        readOpcode = {
                            withTimeout(config.frameIdleTimeout) { it.readByte().toInt() and 0xFF }
                        },
                        readPayload = { channel, prot ->
                            withTimeout(config.frameIdleTimeout) {
                                ByteArray(prot.size).also { channel.readFully(it) }
                            }
                        },
                        writeOpcode = false,
                    ).run(read, write)
                }
            } catch (_: TimeoutCancellationException) {
                // Expected hostile/idle traffic is bounded by the session semaphore and stays quiet.
            } catch (_: EOFException) {
                // A client closing a cache stream is an ordinary session boundary.
            } catch (failure: CancellationException) {
                throw failure
            }
        } finally {
            sessions.release()
        }
    }

    override fun close() = dispatcher.close()
}

private fun js5Dispatcher(workerThreads: Int): ExecutorCoroutineDispatcher =
    Executors.newFixedThreadPool(workerThreads) { task ->
        Thread(task, "js5-worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()
