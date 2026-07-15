package emu.server.world

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** Owns the isolated tick and connection executors for an in-process world service. */
class WorldServerDispatchers(ioWorkerThreads: Int) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    init {
        require(ioWorkerThreads > 0) { "world IO worker count must be positive" }
    }

    val world: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "world-tick").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    val io: ExecutorCoroutineDispatcher =
        runCatching {
            Executors.newFixedThreadPool(ioWorkerThreads) { task ->
                Thread(task, "world-io").apply { isDaemon = true }
            }.asCoroutineDispatcher()
        }.getOrElse { failure ->
            world.close()
            throw failure
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        io.close()
        world.close()
    }
}
