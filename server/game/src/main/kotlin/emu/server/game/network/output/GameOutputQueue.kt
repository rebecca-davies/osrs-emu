package emu.server.game.network.output

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

/**
 * Per-connection bounded output queue. A producer publishes a complete batch immediately or the
 * queue rejects it without waiting on socket IO.
 */
internal class GameOutputQueue(capacity: Int) : GameOutputSink {
    private data class Entry(
        val batch: GameOutputBatch,
        val published: CompletableDeferred<Unit>? = null,
    )

    private val entries = Channel<Entry>(capacity)

    init {
        require(capacity > 0) { "game output queue capacity must be positive" }
    }

    override fun offer(batch: GameOutputBatch): Boolean = entries.trySend(Entry(batch)).isSuccess

    /** Publishes login output before the player is activated in the world. */
    suspend fun submitAndAwait(batch: GameOutputBatch) {
        val published = CompletableDeferred<Unit>()
        entries.send(Entry(batch, published))
        published.await()
    }

    suspend fun run(consumer: suspend (GameOutputBatch) -> Unit) {
        var failure: Throwable? = null
        try {
            for (entry in entries) {
                try {
                    consumer(entry.batch)
                    entry.published?.complete(Unit)
                } catch (cause: Throwable) {
                    entry.published?.completeExceptionally(cause)
                    throw cause
                }
            }
        } catch (cause: Throwable) {
            failure = cause
            entries.close(cause)
            throw cause
        } finally {
            val cause = failure ?: IllegalStateException("game output queue closed before publication")
            while (true) {
                val pending = entries.tryReceive().getOrNull() ?: break
                pending.published?.completeExceptionally(cause)
            }
        }
    }

    fun close(cause: Throwable? = null) {
        entries.close(cause)
    }
}
