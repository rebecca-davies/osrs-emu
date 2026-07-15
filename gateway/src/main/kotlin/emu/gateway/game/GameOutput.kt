package emu.gateway.game

import emu.netcore.message.OutgoingMessage
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.PacketGroupStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

/** One cycle's indivisible, ordered server-to-client output. */
internal data class GameOutputBatch(
    val segments: List<GameOutputSegment>,
) {
    init {
        require(segments.isNotEmpty()) { "game output batch must not be empty" }
    }

    companion object {
        fun packet(message: OutgoingMessage) = GameOutputBatch(listOf(GameOutputSegment.Packets(listOf(message))))
    }
}

/** A plain packet run or a rev-239 atomic packet group within a batch. */
internal sealed interface GameOutputSegment {
    data class Packets(val messages: List<OutgoingMessage>) : GameOutputSegment
    data class PacketGroup(val messages: List<OutgoingMessage>) : GameOutputSegment
}

internal class GameOutputBatchBuilder {
    private val segments = mutableListOf<GameOutputSegment>()

    fun packet(message: OutgoingMessage) {
        packets(listOf(message))
    }

    fun packets(messages: List<OutgoingMessage>) {
        if (messages.isNotEmpty()) segments += GameOutputSegment.Packets(messages.toList())
    }

    fun packetGroup(messages: List<OutgoingMessage>) {
        require(messages.isNotEmpty()) { "packet group must not be empty" }
        segments += GameOutputSegment.PacketGroup(messages.toList())
    }

    fun build() = GameOutputBatch(segments.toList())
}

internal fun gameOutputBatch(block: GameOutputBatchBuilder.() -> Unit): GameOutputBatch =
    GameOutputBatchBuilder().apply(block).build()

/** Non-suspending boundary used by the authoritative world cycle. */
internal fun interface GameOutputSink {
    fun offer(batch: GameOutputBatch): Boolean
}

/**
 * Per-connection bounded mailbox. Producers either enqueue a complete batch immediately or reject
 * it completely; they can never wait on socket IO. The sole consumer owns encoding, ISAAC and the
 * write channel.
 */
internal class GameOutboundMailbox(capacity: Int) : GameOutputSink {
    private data class Entry(
        val batch: GameOutputBatch,
        val published: CompletableDeferred<Unit>? = null,
    )

    private val entries = Channel<Entry>(capacity)

    init {
        require(capacity > 0) { "outbound mailbox capacity must be positive" }
    }

    override fun offer(batch: GameOutputBatch): Boolean = entries.trySend(Entry(batch)).isSuccess

    /** Login-only barrier: the player is not activated until cycle zero reaches the socket. */
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
            val cause = failure ?: IllegalStateException("outbound mailbox closed before publication")
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

/** Expands group declarations and publishes a batch with exactly one socket flush. */
internal class GameOutboundWriter(
    private val session: OutboundSession,
) {
    suspend fun write(batch: GameOutputBatch) {
        val messages = buildList {
            for (segment in batch.segments) {
                when (segment) {
                    is GameOutputSegment.Packets -> addAll(segment.messages)
                    is GameOutputSegment.PacketGroup -> {
                        val length = segment.messages.sumOf(session::wireSize)
                        require(length <= Short.MAX_VALUE) { "packet group too large: $length" }
                        add(PacketGroupStart(length))
                        addAll(segment.messages)
                    }
                }
            }
        }
        session.sendBatch(messages)
    }
}
