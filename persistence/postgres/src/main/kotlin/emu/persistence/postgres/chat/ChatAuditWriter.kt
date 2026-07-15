package emu.persistence.postgres.chat

import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatAuditStore
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/** Bounded worker that admits chat only when its audit entry can be retained for durable delivery. */
class ChatAuditWriter(
    private val store: ChatAuditStore,
    private val config: ChatAuditWriterConfig = ChatAuditWriterConfig(),
) : ChatAuditSink, AutoCloseable {
    private val queue = ArrayBlockingQueue<ChatAuditMessage>(config.capacity)
    private val running = AtomicBoolean(true)
    private val worker = Thread(::runWorker, "chat-audit-writer").apply { isDaemon = true; start() }

    override fun submit(message: ChatAuditMessage): Boolean =
        queue.offer(message).also { admitted ->
            if (!admitted) logger.warn { "chat audit queue saturated; rejecting unauditable message" }
        }

    override fun close() {
        running.set(false)
        worker.interrupt()
        worker.join(config.closeTimeoutMillis)
    }

    private fun runWorker() {
        var retained: List<ChatAuditMessage>? = null
        while (running.get() || retained != null || queue.isNotEmpty()) {
            try {
                val batch = retained ?: nextBatch() ?: continue
                try {
                    store.append(batch)
                    retained = null
                } catch (failure: Throwable) {
                    retained = batch
                    logger.warn(failure) { "chat audit batch write failed; retaining batch for retry" }
                    Thread.sleep(config.retryMillis)
                }
            } catch (_: InterruptedException) {
                if (!running.get()) continue
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
}
