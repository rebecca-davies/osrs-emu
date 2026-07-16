package emu.server.game

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class GameServerDispatchersTest {
    @Test
    fun `blocked connection work cannot delay world entry work`() = runBlocking {
        val connectionStarted = CountDownLatch(1)
        val releaseConnection = CountDownLatch(1)
        GameServerDispatchers(connectionWorkerThreads = 1, entryWorkerThreads = 1).use { dispatchers ->
            val connection =
                async(dispatchers.connections) {
                    connectionStarted.countDown()
                    releaseConnection.await()
                }
            assertTrue(connectionStarted.await(1, TimeUnit.SECONDS))

            val entryThread =
                withContext(dispatchers.entry) {
                    Thread.currentThread().name
                }

            assertTrue(entryThread.startsWith("world-entry-"))
            releaseConnection.countDown()
            connection.await()
        }
    }
}
