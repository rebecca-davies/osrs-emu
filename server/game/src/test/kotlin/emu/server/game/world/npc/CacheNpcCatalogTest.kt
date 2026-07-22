package emu.server.game.world.npc

import emu.cache.def.NpcDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CacheNpcCatalogTest {
    @Test
    fun `named encodable cache definitions retain their footprint size`() {
        val catalog =
            CacheNpcCatalog(
                listOf(
                    NpcDefinition(1, name = "Jal-Nib", size = 2),
                    NpcDefinition(2, name = "Jal-MejRah"),
                    NpcDefinition(3),
                    NpcDefinition(1 shl 14, name = "Too new"),
                ),
            )

        assertEquals(2, requireNotNull(catalog[1]).size)
        assertEquals(1, requireNotNull(catalog[2]).size)
        assertNull(catalog[3])
        assertNull(catalog[1 shl 14])
    }
}
