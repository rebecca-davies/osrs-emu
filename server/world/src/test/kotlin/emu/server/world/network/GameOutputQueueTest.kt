package emu.server.world.network

import emu.transport.message.OutgoingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GameOutputQueueTest {
    @Test
    fun `a full queue rejects a whole batch without suspending the producer`() {
        val queue = GameOutputQueue(capacity = 1)

        assertTrue(queue.offer(GameOutputBatch.packet(TestMessage(1))))
        assertFalse(queue.offer(GameOutputBatch.packet(TestMessage(2))))
    }

    @Test
    fun `a slow writer cannot block producers and queued batches retain order`() = runBlocking {
        val queue = GameOutputQueue(capacity = 1)
        val writerStarted = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val written = mutableListOf<GameOutputBatch>()
        val writer = launch {
            queue.run { batch ->
                written += batch
                writerStarted.complete(Unit)
                releaseWriter.await()
            }
        }

        val first = GameOutputBatch.packet(TestMessage(1))
        val second = GameOutputBatch.packet(TestMessage(2))
        val rejected = GameOutputBatch.packet(TestMessage(3))
        assertTrue(queue.offer(first))
        writerStarted.await()
        assertTrue(queue.offer(second))
        assertFalse(queue.offer(rejected))

        releaseWriter.complete(Unit)
        queue.close()
        writer.join()
        assertEquals(listOf(first, second), written)
    }

    @Test
    fun `tracked initial batch completes only after the writer publishes it`() = runBlocking {
        val queue = GameOutputQueue(capacity = 1)
        val releaseWriter = CompletableDeferred<Unit>()
        val written = CompletableDeferred<Unit>()
        val writer = launch {
            queue.run {
                releaseWriter.await()
                written.complete(Unit)
            }
        }
        val submitter = launch {
            queue.submitAndAwait(GameOutputBatch.packet(TestMessage(1)))
        }

        yield()
        assertFalse(submitter.isCompleted)
        releaseWriter.complete(Unit)
        submitter.join()
        assertTrue(written.isCompleted)
        queue.close()
        writer.join()
    }

    @Test
    fun `a failed writer closes the queue to every producer`() = runBlocking {
        val queue = GameOutputQueue(capacity = 1)
        assertTrue(queue.offer(GameOutputBatch.packet(TestMessage(1))))

        assertFailsWith<IllegalStateException> {
            queue.run { error("socket failed") }
        }

        assertFalse(queue.offer(GameOutputBatch.packet(TestMessage(2))))
    }

    private data class TestMessage(val value: Int) : OutgoingMessage
}
