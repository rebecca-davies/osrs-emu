package emu.game.chat

import kotlin.test.Test
import kotlin.test.assertContentEquals

class PublicChatInputTest {
    @Test
    fun `pattern bytes are copied on construction and access`() {
        val source = byteArrayOf(1, 2, 3)
        val input = PublicChatInput(0, 0, "hello", source)

        source[0] = 9
        val exposed = input.pattern!!
        exposed[1] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), input.pattern)
    }
}
