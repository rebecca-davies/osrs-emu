package emu.server.game.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.ZERO

class GameExecutionConfigTest {
    @Test
    fun `defaults to every mutually visible rev 239 player index`() {
        assertEquals(2_047, GameExecutionConfig().maxConcurrentSessions)
    }

    @Test
    fun `rejects a session count beyond one rev 239 world`() {
        assertFailsWith<IllegalArgumentException> {
            GameExecutionConfig(maxConcurrentSessions = 2_048)
        }
    }

    @Test
    fun `rejects collision loaders that cannot make progress`() {
        assertFailsWith<IllegalArgumentException> {
            CollisionLoadQueueConfig(capacity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CollisionLoadQueueConfig(workerThreads = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CollisionLoadQueueConfig(shutdownTimeout = ZERO)
        }
    }
}
