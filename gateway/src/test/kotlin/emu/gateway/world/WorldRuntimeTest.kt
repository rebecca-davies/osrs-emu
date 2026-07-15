package emu.gateway.world

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        assertTrue(firstRegistration.admitted.await())
        assertTrue(secondRegistration.admitted.await())
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

        assertTrue(admittedRegistration.admitted.await())
        assertFalse(duplicateRegistration.admitted.await())
        assertEquals(listOf(0L), admitted.ticks)
        assertEquals(emptyList(), duplicate.ticks)
        assertTrue(duplicateRegistration.removed.isCompleted)
    }

    @Test
    fun `one failed participant is removed without stopping the world`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds)
        val failed = RecordingParticipant(playerId = 10, failOnTick = 0)
        val healthy = RecordingParticipant(playerId = 11)
        val failedRegistration = runtime.register(failed)
        runtime.register(healthy)

        runtime.run(maxTicks = 3)

        assertEquals(listOf(0L), failed.ticks)
        assertEquals(listOf(0L, 1L, 2L), healthy.ticks)
        assertTrue(failedRegistration.removed.isCompleted)
    }

    @Test
    fun `registration mailbox rejects overflow instead of growing without bound`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds, commandCapacity = 1)
        val accepted = runtime.register(RecordingParticipant(playerId = 20))
        val overflow = runtime.register(RecordingParticipant(playerId = 21))

        assertFalse(overflow.admitted.await())
        assertTrue(overflow.removed.isCompleted)

        runtime.run(maxTicks = 1)
        assertTrue(accepted.admitted.await())
    }

    @Test
    fun `a paused admission does not tick until the login stage activates it`() = runBlocking {
        val runtime = WorldRuntime(tickInterval = 1.milliseconds)
        val participant = RecordingParticipant(playerId = 30)
        val registration = runtime.register(participant, startActive = false)
        val activation = launch {
            assertTrue(registration.admitted.await())
            runtime.activate(participant.playerId)
        }

        runtime.run(maxTicks = 3)

        activation.join()
        assertEquals(listOf(1L, 2L), participant.ticks)
    }

    private class RecordingParticipant(
        override val playerId: Long,
        private val failOnTick: Long? = null,
    ) : WorldParticipant {
        val ticks = mutableListOf<Long>()

        override suspend fun cycle(worldTick: Long): WorldParticipantResult {
            ticks += worldTick
            if (worldTick == failOnTick) error("participant failure")
            return WorldParticipantResult.KEEP
        }
    }
}
