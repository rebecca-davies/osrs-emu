package emu.game.npc

import emu.game.map.MapInstance
import emu.game.map.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpcListTest {
    @Test
    fun `indexes are reused with new identity and instance operations remain isolated`() {
        val npcs = NpcList(capacity = 2)
        val type = NpcType(1, "Jal-Nib", size = 2)
        val firstInstance = MapInstance.privateTo(1)
        val secondInstance = MapInstance.privateTo(2)
        val first = requireNotNull(npcs.add(type, Tile(10, 10), firstInstance, paused = true))
        val firstUid = NpcUid(first.index, first.uid)
        val second = requireNotNull(npcs.add(type, Tile(20, 20), secondInstance))

        assertTrue(npcs.intersects(firstInstance, Tile(11, 11), size = 1))
        assertFalse(npcs.intersects(secondInstance, Tile(11, 11), size = 1))
        assertNull(npcs.add(type, Tile(30, 30), firstInstance))
        assertTrue(npcs.remove(first))
        val replacement = requireNotNull(npcs.add(type, Tile(12, 12), firstInstance))

        assertEquals(first.index, replacement.index)
        assertNotEquals(first.uid, replacement.uid)
        assertNull(npcs.resolve(firstUid))
        assertEquals(replacement, npcs.resolve(NpcUid(replacement.index, replacement.uid)))
        assertEquals(1, npcs.pause(firstInstance, paused = true))
        assertTrue(replacement.paused)
        assertEquals(1, npcs.remove(firstInstance))
        assertEquals(second, npcs[second.index])
        assertEquals(1, npcs.size)
    }
}
