package emu.server.game.world.obj

import emu.cache.def.ItemDefinition
import emu.game.obj.Wearpos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheObjCatalogTest {
    @Test
    fun `cache stack mode and primary appearance slots retain revision semantics`() {
        val catalog =
            CacheObjCatalog(
                listOf(
                    ItemDefinition(1, name = "Coins", stackable = 1),
                    ItemDefinition(2, name = "Placeholder mode", stackable = 2),
                    ItemDefinition(10_556, name = "Attacker icon", wearPos1 = Wearpos.JAW.slot),
                ),
            )

        assertTrue(requireNotNull(catalog[1]).stackable)
        assertFalse(requireNotNull(catalog[2]).stackable)
        assertEquals(Wearpos.JAW, requireNotNull(catalog[10_556]).wearpos)
    }
}
