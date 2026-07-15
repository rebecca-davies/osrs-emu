package emu.persistence.postgres.character

import emu.persistence.character.CharacterStore
import emu.persistence.character.CharacterWriteState
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CharacterSaveWriterTest {
    @Test
    fun `accepted save runs on the writer thread`() {
        val saveStarted = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        var storeThread = ""
        val writer =
            CharacterSaveWriter(
                store =
                    characterStore {
                        storeThread = Thread.currentThread().name
                        saveStarted.countDown()
                        releaseSave.await()
                    },
                config = CharacterSaveWriterConfig(pollMillis = 1, retryMillis = 1),
            )

        val completion = requireNotNull(writer.submit(save(1)))
        assertTrue(saveStarted.await(1, TimeUnit.SECONDS))
        assertFalse(completion.isDurable())
        assertNotEquals(Thread.currentThread().name, storeThread)

        releaseSave.countDown()
        writer.close()
        assertTrue(completion.isDurable())
    }

    @Test
    fun `full queue rejects newest save without discarding accepted saves`() {
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
                config = CharacterSaveWriterConfig(capacity = 2, pollMillis = 1, retryMillis = 1),
            )

        assertTrue(writer.submit(save(1)) != null)
        assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
        assertTrue(writer.submit(save(2)) != null)
        assertEquals(null, writer.submit(save(3)))
        releaseFirst.countDown()
        writer.close()

        assertEquals(listOf(1L, 2L), saved.sorted())
    }

    @Test
    fun `close drains every accepted save before returning`() {
        val saved = Collections.synchronizedList(mutableListOf<Long>())
        val writer =
            CharacterSaveWriter(
                store = characterStore { saved += it.playerId },
                config = CharacterSaveWriterConfig(capacity = 3, pollMillis = 1, retryMillis = 1),
            )

        assertTrue(writer.submit(save(1)) != null)
        assertTrue(writer.submit(save(2)) != null)
        assertTrue(writer.submit(save(3)) != null)
        writer.close()

        assertEquals(listOf(1L, 2L, 3L), saved.sorted())
        assertEquals(null, writer.submit(save(4)))
    }

    @Test
    fun `failed save waits to retry without blocking another player`() {
        val attempts = AtomicInteger()
        val saved = Collections.synchronizedList(mutableListOf<Long>())
        val secondSaved = CountDownLatch(1)
        val writer =
            CharacterSaveWriter(
                store =
                    characterStore { value ->
                        if (attempts.getAndIncrement() == 0) error("temporary failure")
                        saved += value.playerId
                        if (value.playerId == 2L) secondSaved.countDown()
                    },
                config =
                    CharacterSaveWriterConfig(
                        capacity = 2,
                        pollMillis = 1,
                        retryMillis = 20,
                    ),
            )

        assertTrue(writer.submit(save(1)) != null)
        assertTrue(writer.submit(save(2)) != null)
        assertTrue(secondSaved.await(1, TimeUnit.SECONDS))
        writer.close()

        assertEquals(listOf(2L, 1L), saved)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `one player can have only one pending save`() {
        val saveStarted = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        val writer =
            CharacterSaveWriter(
                store =
                    characterStore {
                        saveStarted.countDown()
                        releaseSave.await()
                    },
                config = CharacterSaveWriterConfig(capacity = 2, pollMillis = 1, retryMillis = 1),
            )
        val first = requireNotNull(writer.submit(save(1)))
        assertTrue(saveStarted.await(1, TimeUnit.SECONDS))

        assertNull(writer.submit(save(1).copy(playTimeSeconds = 2)))

        releaseSave.countDown()
        writer.close()
        assertEquals(CharacterWriteState.DURABLE, first.state())
    }

    @Test
    fun `permanent failure completes explicitly instead of hanging shutdown`() {
        val writer =
            CharacterSaveWriter(
                store = characterStore { error("permanent failure") },
                config =
                    CharacterSaveWriterConfig(
                        pollMillis = 1,
                        retryMillis = 1,
                        maxAttempts = 2,
                    ),
            )
        val completion = requireNotNull(writer.submit(save(1)))

        writer.close()

        assertEquals(CharacterWriteState.FAILED, completion.state())
    }

    @Test
    fun `queueing snapshots mutable varps before returning`() {
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

        assertTrue(writer.submit(save(1).copy(dirtyVarps = mutableVarps)) != null)
        assertTrue(enteredStore.await(1, TimeUnit.SECONDS))
        mutableVarps[173] = 0
        releaseStore.countDown()
        writer.close()

        assertEquals(mapOf(173 to 1), storedVarps)
    }

    @Test
    fun `close fails explicitly at its deadline while storage ignores interruption`() {
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
                        closeTimeoutMillis = 10,
                    ),
            )
        assertTrue(writer.submit(save(1)) != null)
        assertTrue(enteredStore.await(1, TimeUnit.SECONDS))

        val failure = assertFailsWith<CharacterSaveShutdownException> { writer.close() }
        assertEquals(1, failure.pendingCount)
        releaseStore.countDown()
    }

    private fun save(playerId: Long) =
        PlayerSessionSave(playerId, PlayerPosition(3222, 3218, 0), playTimeSeconds = 1)

    private fun characterStore(write: (PlayerSessionSave) -> Unit): CharacterStore =
        object : CharacterStore {
            override fun load(playerId: Long): PlayerRecord? = null

            override fun save(save: PlayerSessionSave) = write(save)
        }
}
