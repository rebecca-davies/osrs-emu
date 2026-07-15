package emu.persistence.postgres

import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditStore
import emu.persistence.chat.ChatChannel
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.chat.ChatAuditWriterConfig
import emu.persistence.postgres.chat.ChatAuditShutdownException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ChatAuditWriterTest {
    @Test
    fun `bounded writer batches accepted messages away from the caller thread`() {
        val caller = Thread.currentThread()
        val saved = mutableListOf<ChatAuditMessage>()
        val completed = CountDownLatch(1)
        val store = ChatAuditStore {
            assertFalse(Thread.currentThread() === caller)
            synchronized(saved) { saved += it }
            completed.countDown()
        }
        ChatAuditWriter(
            store,
            ChatAuditWriterConfig(capacity = 2, batchSize = 2, flushMillis = 10),
        ).use { writer ->
            assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "hello", Instant.EPOCH)))
            assertTrue(completed.await(2, TimeUnit.SECONDS))
        }
        assertEquals(listOf("hello"), synchronized(saved) { saved.map { it.text } })
    }

    @Test
    fun `saturation rejects chat instead of allowing an unaudited message`() {
        val blocked = CountDownLatch(1)
        val store = ChatAuditStore { blocked.await(2, TimeUnit.SECONDS) }
        ChatAuditWriter(
            store,
            ChatAuditWriterConfig(capacity = 1, batchSize = 1, flushMillis = 10),
        ).use { writer ->
            assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "first", Instant.EPOCH)))
            Thread.sleep(25)
            assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "second", Instant.EPOCH)))
            assertFalse(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "third", Instant.EPOCH)))
            blocked.countDown()
        }
    }

    @Test
    fun `close fails explicitly at its deadline when storage ignores interruption`() {
        val enteredStore = CountDownLatch(1)
        val releaseStore = CountDownLatch(1)
        val store =
            ChatAuditStore {
                enteredStore.countDown()
                while (releaseStore.count > 0) {
                    try {
                        releaseStore.await()
                    } catch (_: InterruptedException) {
                        // Simulate a blocking JDBC driver that does not honour interruption.
                    }
                }
            }
        val writer =
            ChatAuditWriter(
                store,
                ChatAuditWriterConfig(
                    capacity = 1,
                    batchSize = 1,
                    flushMillis = 1,
                    closeTimeoutMillis = 10,
                ),
            )
        assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "hello", Instant.EPOCH)))
        assertTrue(enteredStore.await(1, TimeUnit.SECONDS))

        val failure = assertFailsWith<ChatAuditShutdownException> { writer.close() }
        assertEquals(1, failure.undeliveredCount)
        releaseStore.countDown()
    }

    @Test
    fun `permanent storage failure exhausts finite shutdown attempts without silent loss`() {
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val firstAttempt = CountDownLatch(1)
        val writer =
            ChatAuditWriter(
                ChatAuditStore {
                    if (attempts.incrementAndGet() == 1) {
                        firstAttempt.countDown()
                        CountDownLatch(1).await()
                    }
                    error("database unavailable")
                },
                ChatAuditWriterConfig(
                    capacity = 1,
                    batchSize = 1,
                    flushMillis = 1,
                    retryMillis = 1,
                    shutdownAttempts = 3,
                    closeTimeoutMillis = 1_000,
                ),
            )
        assertTrue(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "hello", Instant.EPOCH)))
        assertTrue(firstAttempt.await(1, TimeUnit.SECONDS))

        val failure = assertFailsWith<ChatAuditShutdownException> { writer.close() }

        assertEquals(1, failure.undeliveredCount)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `closed writer rejects audit writes`() {
        val writer =
            ChatAuditWriter(
                ChatAuditStore {},
                ChatAuditWriterConfig(flushMillis = 1),
            )

        writer.close()

        assertFalse(writer.submit(ChatAuditMessage(1, ChatChannel.PUBLIC, "late", Instant.EPOCH)))
    }
}
