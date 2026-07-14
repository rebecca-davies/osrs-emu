package emu.game.map

import emu.game.pathfinding.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerBuildAreaTest {
    @Test
    fun `spawn build area is a 13-zone scene centred on the player zone`() {
        val area = PlayerBuildArea(Tile(3222, 3218))

        assertEquals(3168, area.baseX)
        assertEquals(3168, area.baseY)
        assertEquals(402, area.centreZoneX)
        assertEquals(402, area.centreZoneY)
        assertEquals(54, area.localX(3222))
        assertEquals(50, area.localY(3218))
    }

    @Test
    fun `scene recentres when movement enters the outer sixteen tile boundary`() {
        val area = PlayerBuildArea(Tile(3222, 3218))

        assertFalse(area.recenterIfRequired(Tile(3255, 3218)))
        assertTrue(area.recenterIfRequired(Tile(3256, 3218)))
        assertEquals(3208, area.baseX)
        assertEquals(3168, area.baseY)
        assertEquals(407, area.centreZoneX)
        assertEquals(402, area.centreZoneY)
        assertEquals(48, area.localX(3256))
        assertEquals(50, area.localY(3218))
    }
}
