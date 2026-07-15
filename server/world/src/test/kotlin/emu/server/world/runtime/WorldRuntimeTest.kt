package emu.server.world.runtime

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class WorldRuntimeTest {
    @Test
    fun `every participant observes the same authoritative world ticks`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds)
        val first = RecordingParticipant(playerId = 1)
        val second = RecordingParticipant(playerId = 2)
        val firstRegistration = runtime.register(first)
        val secondRegistration = runtime.register(second)

        runtime.run(maxTicks = 3)

        assertEquals(1, firstRegistration.playerIndex.await())
        assertEquals(2, secondRegistration.playerIndex.await())
        assertNotEquals(firstRegistration.playerIndex.await(), secondRegistration.playerIndex.await())
        assertEquals(listOf(0L, 1L, 2L), first.ticks)
        assertEquals(first.ticks, second.ticks)
        assertTrue(firstRegistration.removed.isCompleted)
        assertTrue(secondRegistration.removed.isCompleted)
    }

    @Test
    fun `a duplicate player session is rejected without replacing the admitted session`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds)
        val admitted = RecordingParticipant(playerId = 7)
        val duplicate = RecordingParticipant(playerId = 7)
        val admittedRegistration = runtime.register(admitted)
        val duplicateRegistration = runtime.register(duplicate)

        runtime.run(maxTicks = 1)

        assertEquals(1, admittedRegistration.playerIndex.await())
        assertNull(duplicateRegistration.playerIndex.await())
        assertEquals(listOf(0L), admitted.ticks)
        assertEquals(emptyList(), duplicate.ticks)
        assertTrue(duplicateRegistration.removed.isCompleted)
    }

    @Test
    fun `one failed participant is removed without stopping the world`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 20.milliseconds)
        val failed = RecordingParticipant(playerId = 10, failOnTick = 0)
        val healthy = RecordingParticipant(playerId = 11)
        val failedRegistration = runtime.register(failed)
        runtime.register(healthy)
        lateinit var replacementRegistration: WorldRegistration
        val replacement = launch {
            failedRegistration.removed.await()
            replacementRegistration = runtime.register(RecordingParticipant(playerId = 12))
        }

        runtime.run(maxTicks = 3)

        replacement.join()
        assertEquals(listOf(0L), failed.ticks)
        assertEquals(listOf(0L, 1L, 2L), healthy.ticks)
        assertTrue(failedRegistration.removed.isCompleted)
        assertEquals(1, replacementRegistration.playerIndex.await())
    }

    @Test
    fun `registration mailbox rejects overflow instead of growing without bound`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds, commandCapacity = 1)
        val accepted = runtime.register(RecordingParticipant(playerId = 20))
        val overflow = runtime.register(RecordingParticipant(playerId = 21))

        assertNull(overflow.playerIndex.await())
        assertTrue(overflow.removed.isCompleted)

        runtime.run(maxTicks = 1)
        assertEquals(1, accepted.playerIndex.await())
    }

    @Test
    fun `player index exhaustion rejects admission without disturbing allocated players`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds, commandCapacity = 2_048)
        val registrations = (1L..2_048L).map { playerId ->
            runtime.register(RecordingParticipant(playerId))
        }

        runtime.run(maxTicks = 1)

        val allocated = registrations.dropLast(1).map { it.playerIndex.await() }
        assertEquals((1..2_047).toList(), allocated)
        assertNull(registrations.last().playerIndex.await())
        assertTrue(registrations.last().removed.isCompleted)
    }

    @Test
    fun `a paused admission does not tick until the login stage activates it`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 20.milliseconds)
        val participant = RecordingParticipant(playerId = 30)
        val registration = runtime.register(participant, startActive = false)
        val activation = launch {
            assertEquals(1, registration.playerIndex.await())
            runtime.activate(participant.playerId)
        }

        runtime.run(maxTicks = 3)

        activation.join()
        assertEquals(listOf(1L, 2L), participant.ticks)
    }

    @Test
    fun `released player index is reused in lowest-slot order`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 20.milliseconds)
        val departing = RecordingParticipant(playerId = 40, removeOnTick = 0)
        val staying = RecordingParticipant(playerId = 41)
        val departingRegistration = runtime.register(departing)
        val stayingRegistration = runtime.register(staying)
        lateinit var replacementRegistration: WorldRegistration
        val replacement = launch {
            departingRegistration.removed.await()
            replacementRegistration = runtime.register(RecordingParticipant(playerId = 42))
        }

        runtime.run(maxTicks = 3)

        replacement.join()
        assertEquals(1, departingRegistration.playerIndex.await())
        assertEquals(2, stayingRegistration.playerIndex.await())
        assertEquals(1, replacementRegistration.playerIndex.await())
    }

    @Test
    fun `explicit removal releases the player index for the next admission`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 20.milliseconds)
        val first = RecordingParticipant(playerId = 50)
        val firstRegistration = runtime.register(first)
        lateinit var replacementRegistration: WorldRegistration
        val replacement = launch {
            assertEquals(1, firstRegistration.playerIndex.await())
            runtime.remove(first.playerId)
            firstRegistration.removed.await()
            replacementRegistration = runtime.register(RecordingParticipant(playerId = 51))
        }

        runtime.run(maxTicks = 3)

        replacement.join()
        assertEquals(1, replacementRegistration.playerIndex.await())
    }

    @Test
    fun `shutdown removes admitted players and rejects queued admissions without an index`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds)
        val admitted = runtime.register(RecordingParticipant(playerId = 60))

        runtime.run(maxTicks = 1)
        val afterShutdown = runtime.register(RecordingParticipant(playerId = 61))

        assertEquals(1, admitted.playerIndex.await())
        assertTrue(admitted.removed.isCompleted)
        assertNull(afterShutdown.playerIndex.await())
        assertTrue(afterShutdown.removed.isCompleted)
    }

    @Test
    fun `an overrun yields to activation submitters before the next cycle`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds)
        val overrun = RecordingParticipant(playerId = 31, cycleDelayMillis = 20)
        val participant = RecordingParticipant(playerId = 32)
        runtime.register(overrun)
        val registration = runtime.register(participant, startActive = false)
        val activation = launch {
            assertEquals(2, registration.playerIndex.await())
            runtime.activate(participant.playerId)
        }

        runtime.run(maxTicks = 3)

        activation.join()
        assertEquals(listOf(1L, 2L), participant.ticks)
    }

    private class RecordingParticipant(
        override val playerId: Long,
        private val failOnTick: Long? = null,
        private val removeOnTick: Long? = null,
        private val cycleDelayMillis: Long = 0,
    ) : WorldParticipant {
        val ticks = mutableListOf<Long>()

        override fun cycle(worldTick: Long): WorldParticipantResult {
            ticks += worldTick
            if (cycleDelayMillis > 0) Thread.sleep(cycleDelayMillis)
            if (worldTick == failOnTick) error("participant failure")
            return if (worldTick == removeOnTick) WorldParticipantResult.REMOVE else WorldParticipantResult.KEEP
        }
    }
}
