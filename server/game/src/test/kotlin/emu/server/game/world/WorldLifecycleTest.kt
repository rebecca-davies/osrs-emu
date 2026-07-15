package emu.server.game.world

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class WorldLifecycleTest {
    @Test
    fun `unexpected world failure reaches the lifecycle monitor`() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val failure = IllegalStateException("world failed")
        val lifecycle = WorldLifecycle(dispatcher) { throw failure }
        try {
            lifecycle.start()

            val thrown = assertFailsWith<IllegalStateException> { lifecycle.awaitTermination() }

            assertEquals(failure.message, thrown.message)
            assertFalse(lifecycle.isRunning)
        } finally {
            lifecycle.stop()
            dispatcher.close()
        }
    }
}
