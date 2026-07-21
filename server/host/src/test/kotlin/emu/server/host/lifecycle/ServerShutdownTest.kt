package emu.server.host.lifecycle

import emu.server.bot.BotLaunchResult
import emu.server.bot.BotService
import emu.server.bot.connection.BotEndpoint
import emu.server.game.GameService
import emu.server.session.account.AccountId
import emu.server.session.handoff.ConnectionHandoff
import emu.server.session.handoff.ReservationDecision
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
        val bots = RecordingBotService()
        val gatewayJob = launch { awaitCancellation() }
        val gameMonitor = launch { awaitCancellation() }
        val owner =
            launch {
                coroutineContext[Job]?.cancel()
                shutdownServer(
                    bots = bots,
                    game = game,
                    listener = AutoCloseable { listenerClosed.set(true) },
                    gatewayJob = gatewayJob,
                    gameMonitor = gameMonitor,
                )
            }

        owner.join()

        assertTrue(listenerClosed.get())
        assertTrue(gatewayJob.isCompleted)
        assertTrue(gameMonitor.isCompleted)
        assertEquals(1, bots.stopCount)
        assertEquals(1, game.stopCount)
    }

    private class RecordingBotService : BotService {
        var stopCount = 0

        override fun start(endpoint: BotEndpoint) = Unit

        override fun add(count: Int): BotLaunchResult = error("not used")

        override suspend fun stop() {
            stopCount++
        }
    }

    private class RecordingGameService : GameService {
        var stopCount = 0

        override fun start() = Unit

        override suspend fun awaitTermination() = Unit

        override suspend fun reserve(accountId: AccountId): ReservationDecision =
            error("not used")

        override suspend fun enter(
            read: ByteReadChannel,
            write: ByteWriteChannel,
            handoff: ConnectionHandoff,
            completeLogin: suspend (Int) -> Boolean,
        ): Boolean = false

        override suspend fun stop() {
            stopCount++
        }
    }
}
