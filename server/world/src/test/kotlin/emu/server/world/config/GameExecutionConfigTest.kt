package emu.server.world.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
