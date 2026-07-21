package emu.server.bot

import emu.server.bot.config.BotConfig
import emu.server.bot.connection.BotConnection
import emu.server.bot.connection.BotEndpoint
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

class BotServerTest {
    @Test
    fun `hard client and per-request limits are reserved before connection work`() = runBlocking {
        val server =
            BotServer(
                BotConfig(maxClients = 2, maxPerRequest = 2, maxConcurrentLogins = 1, workerThreads = 1),
                BotConnection { _, _, _ -> awaitCancellation() },
            )
        server.start(LOOPBACK_ENDPOINT)
        try {
            val accepted = assertIs<BotLaunchResult.Accepted>(server.add(2))

            assertEquals(2, accepted.count)
            assertEquals(2, accepted.reservedClients)
            assertEquals(BotLaunchResult.CapacityReached, server.add(1))
            assertEquals(BotLaunchResult.InvalidCount(2), server.add(3))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `requests are unavailable outside the server lifetime`() = runBlocking {
        val server = BotServer(BotConfig(maxClients = 1, maxPerRequest = 1), BotConnection { _, _, _ -> Unit })

        assertEquals(BotLaunchResult.Unavailable, server.add(1))
    }

    private companion object {
        val LOOPBACK_ENDPOINT = BotEndpoint(InetAddress.getLoopbackAddress(), 43_594)
    }
}
