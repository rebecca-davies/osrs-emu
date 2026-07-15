package emu.persistence.postgres.character

import emu.persistence.character.CharacterWriteQueue
import emu.persistence.character.CharacterWriteCompletion
import emu.persistence.character.CharacterWriteState
import emu.persistence.character.CharacterStore
import emu.persistence.character.PlayerSessionSave
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Delayed
import java.util.concurrent.DelayQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/** Delivers bounded character save points to blocking storage on one dedicated worker. */
class CharacterSaveWriter(
    private val store: CharacterStore,
    private val config: CharacterSaveWriterConfig,
) : CharacterWriteQueue, AutoCloseable {
    private val saves = DelayQueue<PendingSave>()
    private val capacity = Semaphore(config.capacity)
    private val pendingPlayerIds = ConcurrentHashMap.newKeySet<Long>()
    private val sequence = AtomicLong()
    private val lifecycle = Any()
    private val accepting = AtomicBoolean(true)
    private val storageUnavailable = AtomicBoolean(false)
    private val closeFailure = AtomicReference<CharacterSaveShutdownException?>()
    private val closed = CountDownLatch(1)
    private val worker = Thread(::runWorker, "character-save-writer").apply { isDaemon = true; start() }

    override fun submit(save: PlayerSessionSave): CharacterWriteCompletion? =
        synchronized(lifecycle) {
            if (!accepting.get()) return@synchronized null
            if (!capacity.tryAcquire()) return@synchronized null
            if (!pendingPlayerIds.add(save.playerId)) {
                capacity.release()
                return@synchronized null
            }
            val pending = PendingSave(save.copy(dirtyVarps = save.dirtyVarps.toMap()))
            saves.offer(pending)
            pending
        }

    override fun close() {
        val shouldClose =
            synchronized(lifecycle) {
                if (!accepting.compareAndSet(true, false)) return@synchronized false
                true
            }
        if (!shouldClose) {
            awaitClosed()
            closeFailure.get()?.let { throw it }
            return
        }

        try {
            accelerateRetries()
            worker.interrupt()
            awaitWorker()
        } finally {
            closed.countDown()
        }
        closeFailure.get()?.let { throw it }
    }

    private fun awaitWorker() {
        var interrupted = false
        try {
            worker.join(config.closeTimeoutMillis)
        } catch (failure: InterruptedException) {
            interrupted = true
            closeFailure.compareAndSet(
                null,
                CharacterSaveShutdownException(pendingCount(), failure),
            )
        }
        if (worker.isAlive) {
            closeFailure.compareAndSet(
                null,
                CharacterSaveShutdownException(pendingCount()),
            )
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private fun pendingCount(): Int = config.capacity - capacity.availablePermits()

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
        while (accepting.get() || capacity.availablePermits() < config.capacity) {
            try {
                val pending = saves.poll(config.pollMillis, TimeUnit.MILLISECONDS) ?: continue
                pending.attempts++
                try {
                    store.save(pending.save)
                    pending.complete()
                    finish(pending)
                    if (storageUnavailable.compareAndSet(true, false)) {
                        logger.info { "character storage recovered" }
                    }
                } catch (failure: Exception) {
                    if (storageUnavailable.compareAndSet(false, true)) {
                        logger.warn(failure) { "character storage unavailable; retaining accepted saves" }
                    }
                    if (pending.attempts >= config.maxAttempts) {
                        pending.fail()
                        finish(pending)
                    } else {
                        pending.retryAfter(if (accepting.get()) config.retryMillis else 0)
                        saves.offer(pending)
                    }
                }
            } catch (_: InterruptedException) {
                // Closing interrupts polling so accepted writes can drain without the idle wait.
            }
        }
    }

    private fun finish(pending: PendingSave) {
        check(pendingPlayerIds.remove(pending.save.playerId)) { "pending player save was not tracked" }
        capacity.release()
    }

    private fun accelerateRetries() {
        for (value in saves.toArray()) {
            val pending = value as PendingSave
            if (!saves.remove(pending)) continue
            pending.retryAfter(0)
            saves.offer(pending)
        }
    }

    private inner class PendingSave(val save: PlayerSessionSave) : CharacterWriteCompletion, Delayed {
        private val order = sequence.getAndIncrement()
        private val writeState = AtomicReference(CharacterWriteState.PENDING)
        @Volatile private var readyAtNanos = System.nanoTime()
        var attempts: Int = 0

        override fun state(): CharacterWriteState = writeState.get()

        override fun getDelay(unit: TimeUnit): Long =
            unit.convert(readyAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS)

        override fun compareTo(other: Delayed): Int {
            val pending = other as PendingSave
            val readyOrder = readyAtNanos.compareTo(pending.readyAtNanos)
            return if (readyOrder != 0) readyOrder else order.compareTo(pending.order)
        }

        fun complete() {
            check(writeState.compareAndSet(CharacterWriteState.PENDING, CharacterWriteState.DURABLE))
        }

        fun fail() {
            check(writeState.compareAndSet(CharacterWriteState.PENDING, CharacterWriteState.FAILED))
        }

        fun retryAfter(delayMillis: Long) {
            readyAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis)
        }
    }
}
