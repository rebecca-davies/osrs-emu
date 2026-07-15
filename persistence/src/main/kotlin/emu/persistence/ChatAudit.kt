package emu.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/** Audited chat channel identifiers stored independently of revision-specific packet types. */
enum class ChatChannel(val id: Int) {
    PUBLIC(0),
}

/** Immutable append-only audit row admitted before a chat message is published. */
data class ChatAuditMessage(
    val playerId: Long,
    val channel: ChatChannel,
    val message: String,
    val createdAt: Instant,
) {
    init {
        require(playerId > 0) { "chat audit player id must be positive" }
        require(message.isNotBlank() && message.length <= MAX_MESSAGE_LENGTH) { "invalid audited chat length" }
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 100
    }
}

/** Non-blocking audit admission boundary used by the game cycle. */
fun interface ChatAuditSink {
    fun submit(message: ChatAuditMessage): Boolean
}

/**
 * Bounded asynchronous audit writer. JDBC runs exclusively on its daemon worker; a retained failed
 * batch is retried before new messages, naturally filling the admission queue and stopping chat
 * publication rather than allowing unaudited messages through.
 */
class ChatAuditWriter(
    private val writeBatch: (List<ChatAuditMessage>) -> Unit,
    capacity: Int = DEFAULT_CAPACITY,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val flushMillis: Long = DEFAULT_FLUSH_MILLIS,
    private val retryMillis: Long = DEFAULT_RETRY_MILLIS,
) : ChatAuditSink, AutoCloseable {
    private val queue: ArrayBlockingQueue<ChatAuditMessage>
    private val running = AtomicBoolean(true)
    private val worker: Thread

    init {
        require(capacity > 0 && batchSize > 0) { "chat audit queue and batch sizes must be positive" }
        require(flushMillis > 0 && retryMillis > 0) { "chat audit timing must be positive" }
        queue = ArrayBlockingQueue(capacity)
        worker = Thread(::runWorker, "chat-audit-writer").apply { isDaemon = true; start() }
    }

    override fun submit(message: ChatAuditMessage): Boolean =
        queue.offer(message).also { admitted ->
            if (!admitted) logger.warn { "chat audit queue saturated; rejecting unauditable message" }
        }

    override fun close() {
        running.set(false)
        worker.interrupt()
        worker.join(CLOSE_TIMEOUT_MILLIS)
    }

    private fun runWorker() {
        var retained: List<ChatAuditMessage>? = null
        while (running.get() || retained != null || queue.isNotEmpty()) {
            try {
                val batch = retained ?: nextBatch() ?: continue
                try {
                    writeBatch(batch)
                    retained = null
                } catch (failure: Throwable) {
                    retained = batch
                    logger.warn(failure) { "chat audit batch write failed; retaining batch for retry" }
                    Thread.sleep(retryMillis)
                }
            } catch (_: InterruptedException) {
                if (!running.get()) continue
            }
        }
    }

    private fun nextBatch(): List<ChatAuditMessage>? {
        val first = queue.poll(flushMillis, TimeUnit.MILLISECONDS) ?: return null
        val batch = ArrayList<ChatAuditMessage>(batchSize)
        batch += first
        queue.drainTo(batch, batchSize - 1)
        return batch
    }

    private companion object {
        const val DEFAULT_CAPACITY = 1024
        const val DEFAULT_BATCH_SIZE = 64
        const val DEFAULT_FLUSH_MILLIS = 100L
        const val DEFAULT_RETRY_MILLIS = 1000L
        const val CLOSE_TIMEOUT_MILLIS = 5000L
    }
}
