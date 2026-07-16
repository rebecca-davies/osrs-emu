package emu.server.game

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** Owns isolated world-tick, active-connection, and world-entry executors. */
class GameServerDispatchers(
    connectionWorkerThreads: Int,
    entryWorkerThreads: Int,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    init {
        require(connectionWorkerThreads > 0) { "game connection worker count must be positive" }
        require(entryWorkerThreads > 0) { "world entry worker count must be positive" }
    }

    val world: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "world-tick").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    val connections: ExecutorCoroutineDispatcher =
        runCatching {
            fixedDispatcher(connectionWorkerThreads, "game-connection")
        }.getOrElse { failure ->
            world.close()
            throw failure
        }

    val entry: ExecutorCoroutineDispatcher =
        runCatching {
            fixedDispatcher(entryWorkerThreads, "world-entry")
        }.getOrElse { failure ->
            connections.close()
            world.close()
            throw failure
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        entry.close()
        connections.close()
        world.close()
    }
}

private fun fixedDispatcher(workerThreads: Int, threadPrefix: String): ExecutorCoroutineDispatcher {
    val sequence = AtomicInteger()
    return Executors.newFixedThreadPool(workerThreads) { task ->
        Thread(task, "$threadPrefix-${sequence.incrementAndGet()}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
