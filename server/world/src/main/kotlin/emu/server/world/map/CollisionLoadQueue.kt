package emu.server.world.map

import emu.game.pathfinding.Tile
import emu.server.world.config.CollisionLoadQueueConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

/** Bounded workers that keep cache decoding and file reads off the authoritative world thread. */
class CollisionLoadQueue(
    private val collision: CacheCollisionMap,
    private val config: CollisionLoadQueueConfig = CollisionLoadQueueConfig(),
) : CollisionMapLoader, AutoCloseable {
    private val accepting = AtomicBoolean(true)
    private val failureReported = AtomicBoolean(false)
    private val requests = ArrayBlockingQueue<Int>(config.capacity)
    private val pending = ConcurrentHashMap.newKeySet<Int>()
    private val workers =
        List(config.workerThreads) { worker ->
            thread(
                start = true,
                isDaemon = true,
                name = "collision-load-${worker + 1}",
                block = ::runWorker,
            )
        }

    override fun prepare(position: Tile) {
        check(accepting.get()) { "collision loader is closed" }
        collision.loadAround(position, LOAD_RADIUS)
    }

    override fun request(position: Tile): Boolean {
        if (!accepting.get() || collision.isLoadedAround(position, LOAD_RADIUS)) return accepting.get()
        val key = squareKey(position)
        if (!pending.add(key)) return true
        if (!accepting.get() || !requests.offer(key)) {
            pending.remove(key)
            return false
        }
        return true
    }

    override fun close() {
        if (!accepting.compareAndSet(true, false)) return
        requests.clear()
        pending.clear()
        workers.forEach(Thread::interrupt)
        val deadline = System.nanoTime() + config.shutdownTimeout.inWholeNanoseconds
        for (worker in workers) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) break
            worker.join(TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1))
        }
        workers.filter(Thread::isAlive).forEach { worker ->
            logger.warn { "collision loader ${worker.name} did not stop before its deadline" }
        }
    }

    private fun runWorker() {
        while (accepting.get()) {
            val key =
                try {
                    requests.take()
                } catch (_: InterruptedException) {
                    continue
            }
            try {
                collision.loadAround(tileAt(key), LOAD_RADIUS)
            } catch (failure: Exception) {
                if (failureReported.compareAndSet(false, true)) {
                    logger.error(failure) {
                        "collision: cache loading failed; suppressing subsequent map-square failures"
                    }
                }
            } finally {
                pending.remove(key)
            }
        }
    }

    private companion object {
        const val MAP_SQUARE_SHIFT = 6
        const val LOAD_RADIUS = 1

        fun squareKey(position: Tile): Int =
            (position.x shr MAP_SQUARE_SHIFT) shl 8 or (position.y shr MAP_SQUARE_SHIFT)

        fun tileAt(key: Int): Tile =
            Tile((key ushr 8) shl MAP_SQUARE_SHIFT, (key and 0xFF) shl MAP_SQUARE_SHIFT)
    }
}
