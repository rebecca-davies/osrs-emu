package emu.game.loc

import emu.game.map.Tile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocTest {
    private val portal =
        Loc(
            type = 26_642,
            tile = Tile(3_126, 3_620),
            shape = 10,
            angle = 1,
            width = 1,
            length = 4,
            options = setOf(1, 2, 3),
        )

    @Test
    fun `plain portal operation does not accept an invented sub-option`() {
        assertTrue(portal.supports(option = 1, subOption = 0))
        assertFalse(portal.supports(option = 1, subOption = 1))
        assertFalse(portal.supports(option = 4, subOption = 0))
    }
}
