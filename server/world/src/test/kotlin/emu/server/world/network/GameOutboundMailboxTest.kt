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

class GameOutboundMailboxTest {
    @Test
    fun `a full mailbox rejects a whole batch without suspending the producer`() {
        val mailbox = GameOutboundMailbox(capacity = 1)

        assertTrue(mailbox.offer(GameOutputBatch.packet(TestMessage(1))))
        assertFalse(mailbox.offer(GameOutputBatch.packet(TestMessage(2))))
    }

    @Test
    fun `a slow writer cannot block producers and queued batches retain order`() = runBlocking {
        val mailbox = GameOutboundMailbox(capacity = 1)
        val writerStarted = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val written = mutableListOf<GameOutputBatch>()
        val writer = launch {
            mailbox.run { batch ->
                written += batch
                writerStarted.complete(Unit)
                releaseWriter.await()
            }
        }

        val first = GameOutputBatch.packet(TestMessage(1))
        val second = GameOutputBatch.packet(TestMessage(2))
        val rejected = GameOutputBatch.packet(TestMessage(3))
        assertTrue(mailbox.offer(first))
        writerStarted.await()
        assertTrue(mailbox.offer(second))
        assertFalse(mailbox.offer(rejected))

        releaseWriter.complete(Unit)
        mailbox.close()
        writer.join()
        assertEquals(listOf(first, second), written)
    }

    @Test
    fun `tracked initial batch completes only after the writer publishes it`() = runBlocking {
        val mailbox = GameOutboundMailbox(capacity = 1)
        val releaseWriter = CompletableDeferred<Unit>()
        val written = CompletableDeferred<Unit>()
        val writer = launch {
            mailbox.run {
                releaseWriter.await()
                written.complete(Unit)
            }
        }
        val submitter = launch {
            mailbox.submitAndAwait(GameOutputBatch.packet(TestMessage(1)))
        }

        yield()
        assertFalse(submitter.isCompleted)
        releaseWriter.complete(Unit)
        submitter.join()
        assertTrue(written.isCompleted)
        mailbox.close()
        writer.join()
    }

    @Test
    fun `a failed writer closes the mailbox to every producer`() = runBlocking {
        val mailbox = GameOutboundMailbox(capacity = 1)
        assertTrue(mailbox.offer(GameOutputBatch.packet(TestMessage(1))))

        assertFailsWith<IllegalStateException> {
            mailbox.run { error("socket failed") }
        }

        assertFalse(mailbox.offer(GameOutputBatch.packet(TestMessage(2))))
    }

    private data class TestMessage(val value: Int) : OutgoingMessage
}
