package emu.persistence.postgres.character

import emu.persistence.character.CharacterStore
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.character.PlayerSessionSave
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CharacterSaveWriterTest {
    @Test
    fun `accepted save runs on the writer thread`() {
        val saved = CountDownLatch(1)
        var storeThread = ""
        val writer =
            CharacterSaveWriter(
                store = characterStore { storeThread = Thread.currentThread().name; saved.countDown() },
                config = CharacterSaveWriterConfig(pollMillis = 1, retryMillis = 1),
            )

        assertTrue(writer.submit(save(1)))
        assertTrue(saved.await(1, TimeUnit.SECONDS))
        assertNotEquals(Thread.currentThread().name, storeThread)

        writer.close()
    }

    @Test
    fun `full queue rejects newest save without discarding admitted saves`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val saved = Collections.synchronizedList(mutableListOf<Long>())
        val writer =
            CharacterSaveWriter(
                store =
                    characterStore { value ->
                        if (value.playerId == 1L) {
                            firstStarted.countDown()
                            releaseFirst.await()
                        }
                        saved += value.playerId
                    },
                config = CharacterSaveWriterConfig(capacity = 1, pollMillis = 1, retryMillis = 1),
            )

        assertTrue(writer.submit(save(1)))
        assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
        assertTrue(writer.submit(save(2)))
        assertFalse(writer.submit(save(3)))
        releaseFirst.countDown()
        writer.close()

        assertEquals(listOf(1L, 2L), saved)
    }

    @Test
    fun `close drains every admitted save before returning`() {
        val saved = Collections.synchronizedList(mutableListOf<Long>())
        val writer =
            CharacterSaveWriter(
                store = characterStore { saved += it.playerId },
                config = CharacterSaveWriterConfig(capacity = 3, pollMillis = 1, retryMillis = 1),
            )

        assertTrue(writer.submit(save(1)))
        assertTrue(writer.submit(save(2)))
        assertTrue(writer.submit(save(3)))
        writer.close()

        assertEquals(listOf(1L, 2L, 3L), saved)
        assertFalse(writer.submit(save(4)))
    }

    @Test
    fun `failed save is retried before later saves`() {
        val attempts = AtomicInteger()
        val saved = Collections.synchronizedList(mutableListOf<Long>())
        val writer =
            CharacterSaveWriter(
                store =
                    characterStore { value ->
                        if (attempts.getAndIncrement() == 0) error("temporary failure")
                        saved += value.playerId
                    },
                config = CharacterSaveWriterConfig(capacity = 2, pollMillis = 1, retryMillis = 1),
            )

        assertTrue(writer.submit(save(1)))
        assertTrue(writer.submit(save(2)))
        writer.close()

        assertEquals(listOf(1L, 2L), saved)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `admission snapshots mutable varps before returning`() {
        val enteredStore = CountDownLatch(1)
        val releaseStore = CountDownLatch(1)
        var storedVarps: Map<Int, Int> = emptyMap()
        val writer =
            CharacterSaveWriter(
                store =
                    characterStore { value ->
                        enteredStore.countDown()
                        releaseStore.await()
                        storedVarps = value.dirtyVarps
                    },
                config = CharacterSaveWriterConfig(pollMillis = 1, retryMillis = 1),
            )
        val mutableVarps = mutableMapOf(173 to 1)

        assertTrue(writer.submit(save(1).copy(dirtyVarps = mutableVarps)))
        assertTrue(enteredStore.await(1, TimeUnit.SECONDS))
        mutableVarps[173] = 0
        releaseStore.countDown()
        writer.close()

        assertEquals(mapOf(173 to 1), storedVarps)
    }

    @Test
    fun `close waits beyond warning threshold while storage is still active`() {
        val enteredStore = CountDownLatch(1)
        val releaseStore = CountDownLatch(1)
        val writer =
            CharacterSaveWriter(
                store =
                    characterStore {
                        enteredStore.countDown()
                        while (releaseStore.count > 0) {
                            try {
                                releaseStore.await()
                            } catch (_: InterruptedException) {
                                // Simulate a blocking JDBC driver that does not honour interruption.
                            }
                        }
                    },
                config =
                    CharacterSaveWriterConfig(
                        pollMillis = 1,
                        retryMillis = 1,
                        closeWarningMillis = 10,
                    ),
            )
        assertTrue(writer.submit(save(1)))
        assertTrue(enteredStore.await(1, TimeUnit.SECONDS))

        val closer = Thread(writer::close).apply { start() }
        Thread.sleep(50)

        assertTrue(closer.isAlive)
        releaseStore.countDown()
        closer.join(1_000)
        assertFalse(closer.isAlive)
    }

    private fun save(playerId: Long) =
        PlayerSessionSave(playerId, PlayerPosition(3222, 3218, 0), playedSeconds = 1)

    private fun characterStore(write: (PlayerSessionSave) -> Unit): CharacterStore =
        object : CharacterStore {
            override fun load(playerId: Long): PlayerRecord? = null

            override fun save(save: PlayerSessionSave) = write(save)
        }
}
