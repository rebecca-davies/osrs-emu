package emu.game.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ButtonClickTest {
    @Test
    fun `ordinary button defaults optional wire fields and operation one`() {
        assertEquals(ButtonClick(160, 28, -1, -1, 1), ButtonClick(160, 28))
    }

    @Test
    fun `component and optional wire fields remain unsigned-short values`() {
        assertFailsWith<IllegalArgumentException> { ButtonClick(0x1_0000, 0) }
        assertFailsWith<IllegalArgumentException> { ButtonClick(0, 0, sub = -2) }
        assertFailsWith<IllegalArgumentException> { ButtonClick(0, 0, obj = 0x1_0000) }
    }
}
