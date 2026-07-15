package emu.persistence

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatAuditWriterTest {
    @Test fun `bounded writer batches accepted messages away from the caller thread`() {
        val caller = Thread.currentThread()
        val saved = mutableListOf<ChatAuditMessage>()
        val completed = CountDownLatch(1)
        ChatAuditWriter(
            writeBatch = {
                assertFalse(Thread.currentThread() === caller)
                synchronized(saved) { saved += it }
                completed.countDown()
            },
            capacity = 2,
            batchSize = 2,
            flushMillis = 10,
        ).use { writer ->
            assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "hello", Instant.EPOCH)))
            assertTrue(completed.await(2, TimeUnit.SECONDS))
        }
        assertEquals(listOf("hello"), synchronized(saved) { saved.map { it.message } })
    }

    @Test fun `saturation rejects chat instead of allowing an unaudited message`() {
        val blocked = CountDownLatch(1)
        ChatAuditWriter(
            writeBatch = { blocked.await(2, TimeUnit.SECONDS) },
            capacity = 1,
            batchSize = 1,
            flushMillis = 10,
        ).use { writer ->
            assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "first", Instant.EPOCH)))
            Thread.sleep(25)
            assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "second", Instant.EPOCH)))
            assertFalse(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "third", Instant.EPOCH)))
            blocked.countDown()
        }
    }
}
