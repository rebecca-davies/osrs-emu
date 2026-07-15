package emu.server.host

import emu.server.session.AuthenticatedPrincipal
import emu.server.session.ConnectionHandoff
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import emu.server.world.GameService
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ServerShutdownTest {
    @Test
    fun `cancelled owner still closes listener and jobs before stopping game`() = runBlocking {
        val listenerClosed = AtomicBoolean(false)
        val game = RecordingGameService()
        val gatewayJob = launch { awaitCancellation() }
        val worldMonitor = launch { awaitCancellation() }
        val owner =
            launch {
                coroutineContext[Job]?.cancel()
                shutdownServer(
                    game = game,
                    listener = AutoCloseable { listenerClosed.set(true) },
                    gatewayJob = gatewayJob,
                    worldMonitor = worldMonitor,
                    shutdownHook = null,
                )
            }

        owner.join()

        assertTrue(listenerClosed.get())
        assertTrue(gatewayJob.isCompleted)
        assertTrue(worldMonitor.isCompleted)
        assertEquals(1, game.stopCount)
    }

    private class RecordingGameService : GameService {
        var stopCount = 0

        override fun start() = Unit

        override suspend fun awaitTermination() = Unit

        override suspend fun reserve(principal: AuthenticatedPrincipal): ReservationDecision =
            error("not used")

        override suspend fun release(token: GameSessionToken) = Unit

        override suspend fun play(
            read: ByteReadChannel,
            write: ByteWriteChannel,
            handoff: ConnectionHandoff,
        ) = Unit

        override suspend fun stop() {
            stopCount++
        }
    }
}
