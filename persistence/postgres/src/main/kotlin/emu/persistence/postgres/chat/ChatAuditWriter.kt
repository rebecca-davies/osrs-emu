package emu.persistence.postgres.chat

import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatAuditStore
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/** Bounded worker that either durably delivers accepted audit entries or fails shutdown explicitly. */
class ChatAuditWriter(
    private val store: ChatAuditStore,
    private val config: ChatAuditWriterConfig = ChatAuditWriterConfig(),
) : ChatAuditSink, AutoCloseable {
    private val queue = ArrayBlockingQueue<ChatAuditMessage>(config.capacity)
    private val lifecycle = Any()
    private val running = AtomicBoolean(true)
    private val retainedCount = AtomicInteger()
    private val failure = AtomicReference<ChatAuditShutdownException?>()
    private val closed = CountDownLatch(1)
    private val worker = Thread(::runWorker, "chat-audit-writer").apply { isDaemon = true; start() }

    override fun submit(message: ChatAuditMessage): Boolean =
        synchronized(lifecycle) {
            if (!running.get()) return@synchronized false
            queue.offer(message)
        }

    override fun close() {
        val owner =
            synchronized(lifecycle) {
                running.compareAndSet(true, false)
            }
        if (!owner) {
            awaitClosed()
            failure.get()?.let { throw it }
            return
        }
        try {
            worker.interrupt()
            worker.join(config.closeTimeoutMillis)
            if (worker.isAlive) {
                failShutdown()
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            failShutdown(interrupted)
        } finally {
            closed.countDown()
        }
        failure.get()?.let { throw it }
    }

    private fun runWorker() {
        var retained: List<ChatAuditMessage>? = null
        var shutdownFailures = 0
        var outage = false
        while (running.get() || retained != null || queue.isNotEmpty()) {
            try {
                val batch = retained ?: nextBatch() ?: continue
                retainedCount.set(batch.size)
                try {
                    store.append(batch)
                    retained = null
                    retainedCount.set(0)
                    shutdownFailures = 0
                    if (outage) logger.info { "chat audit storage recovered" }
                    outage = false
                } catch (storeFailure: Throwable) {
                    retained = batch
                    if (!outage) logger.warn(storeFailure) { "chat audit storage unavailable; retaining entries" }
                    outage = true
                    if (!running.get() && ++shutdownFailures >= config.shutdownAttempts) {
                        failShutdown(storeFailure)
                        return
                    }
                    Thread.sleep(if (running.get()) config.retryMillis else 1)
                }
            } catch (_: InterruptedException) {
                // Closing interrupts polling/retry so the finite shutdown attempt policy runs now.
            }
        }
    }

    private fun nextBatch(): List<ChatAuditMessage>? {
        val first = queue.poll(config.flushMillis, TimeUnit.MILLISECONDS) ?: return null
        val batch = ArrayList<ChatAuditMessage>(config.batchSize)
        batch += first
        queue.drainTo(batch, config.batchSize - 1)
        return batch
    }

    private fun failShutdown(cause: Throwable? = null) {
        failure.compareAndSet(
            null,
            ChatAuditShutdownException(queue.size + retainedCount.get(), cause),
        )
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
}
