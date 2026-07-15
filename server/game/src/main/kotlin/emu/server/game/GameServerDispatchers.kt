package emu.server.game

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** Owns the isolated world and game-connection executors for an in-process game service. */
internal class GameServerDispatchers(ioWorkerThreads: Int) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    init {
        require(ioWorkerThreads > 0) { "game IO worker count must be positive" }
    }

    val world: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "game-world").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    val io: ExecutorCoroutineDispatcher =
        runCatching {
            Executors.newFixedThreadPool(ioWorkerThreads) { task ->
                Thread(task, "game-io").apply { isDaemon = true }
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
