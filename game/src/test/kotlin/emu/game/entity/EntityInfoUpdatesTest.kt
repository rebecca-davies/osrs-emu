package emu.game.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityInfoUpdatesTest {
    @Test
    fun `spot animation slots are unique replaceable and bounded`() {
        val updates = EntityInfoUpdates()

        assertTrue(updates.playSpotAnimation(EntitySpotAnimation(id = 1, slot = 2)))
        assertTrue(updates.playSpotAnimation(EntitySpotAnimation(id = 2, slot = 2)))
        assertTrue(updates.playSpotAnimation(EntitySpotAnimation(id = 3, slot = 3)))
        assertTrue(updates.playSpotAnimation(EntitySpotAnimation(id = 4, slot = 4)))
        assertTrue(updates.playSpotAnimation(EntitySpotAnimation(id = 5, slot = 5)))
        assertFalse(updates.playSpotAnimation(EntitySpotAnimation(id = 6, slot = 6)))

        val animations = requireNotNull(updates.snapshot()).spotAnimations
        assertEquals(listOf(2, 3, 4, 5), animations.map(EntitySpotAnimation::id))
        assertEquals(animations.size, animations.map(EntitySpotAnimation::slot).distinct().size)
    }

    @Test
    fun `health bar delay excludes the client removal sentinel`() {
        EntityHealthBar(current = 1, maximum = 1, delay = 0x7FFE)
        assertFailsWith<IllegalArgumentException> {
            EntityHealthBar(current = 1, maximum = 1, delay = 0x7FFF)
        }
    }
}
