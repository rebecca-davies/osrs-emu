package emu.persistence.postgres.character

import emu.persistence.character.CharacterSaveSink
import emu.persistence.character.CharacterStore
import emu.persistence.character.PlayerSessionSave
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/** Delivers bounded character save points to blocking storage on one dedicated worker. */
class CharacterSaveWriter(
    private val store: CharacterStore,
    private val config: CharacterSaveWriterConfig,
) : CharacterSaveSink, AutoCloseable {
    private val saves = ArrayBlockingQueue<PlayerSessionSave>(config.capacity)
    private val lifecycle = Any()
    private val accepting = AtomicBoolean(true)
    private val closed = CountDownLatch(1)
    private val worker = Thread(::runWorker, "character-save-writer").apply { isDaemon = true; start() }

    override fun submit(save: PlayerSessionSave): Boolean =
        synchronized(lifecycle) {
            if (!accepting.get()) return@synchronized false
            saves.offer(save.copy(dirtyVarps = save.dirtyVarps.toMap()))
        }

    override fun close() {
        val shouldClose =
            synchronized(lifecycle) {
                if (!accepting.compareAndSet(true, false)) return@synchronized false
                true
            }
        if (!shouldClose) {
            awaitClosed()
            return
        }

        try {
            worker.interrupt()
            awaitWorker()
        } finally {
            closed.countDown()
        }
    }

    private fun awaitWorker() {
        var interrupted = false
        try {
            worker.join(config.closeWarningMillis)
        } catch (_: InterruptedException) {
            interrupted = true
        }
        if (worker.isAlive) {
            logger.warn {
                "character save writer exceeded ${config.closeWarningMillis}ms shutdown warning threshold; " +
                    "waiting for durable delivery before closing storage"
            }
        }
        while (worker.isAlive) {
            try {
                worker.join()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private fun awaitClosed() {
        var interrupted = false
        while (closed.count > 0) {
            try {
                closed.await()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private fun runWorker() {
        var retained: PlayerSessionSave? = null
        while (accepting.get() || retained != null || saves.isNotEmpty()) {
            try {
                val save = retained ?: saves.poll(config.pollMillis, TimeUnit.MILLISECONDS) ?: continue
                try {
                    store.save(save)
                    retained = null
                } catch (failure: Exception) {
                    retained = save
                    logger.warn(failure) { "character save failed for player ${save.playerId}; retaining for retry" }
                    Thread.sleep(config.retryMillis)
                }
            } catch (_: InterruptedException) {
                // Closing interrupts polling or retry delay so the worker can drain immediately.
            }
        }
    }
}
