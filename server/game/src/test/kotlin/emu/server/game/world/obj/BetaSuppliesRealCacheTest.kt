package emu.server.game.world.obj

import emu.cache.def.CacheItemDefinitionCatalog
import emu.cache.store.FlatFileStore
import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.beta.BetaWorldContentCatalog
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.GameMap
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.pathfinding.collision.OpenCollisionMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BetaSuppliesRealCacheTest {
    @Test
    fun `revision 239 resolves the stock catalogue and Inferno presets`() {
        val cache =
            listOf(File("../../cache-data"), File("../../../osrsemu/cache-data"))
                .firstOrNull { File(it, "cache/255/2.dat").isFile }
                ?: return
        val store = FlatFileStore(cache)
        val objs = CacheObjCatalog(CacheItemDefinitionCatalog(store).definitions)
        val enums = CacheNamedObjEnumCatalog(store)
        val permitted = assertNotNull(enums[1_124])

        assertEquals(628, permitted.size)
        assertTrue(permitted.all { objs[it] != null })
        BetaWorldContentCatalog.load(
            ui = UiContentCatalog.load(),
            inferno =
                InfernoArena(
                    GameMap(OpenCollisionMap),
                    NpcCatalog.EMPTY,
                    NpcList(),
                    InfernoFreeModeCatalog.load(),
                ),
            objs = objs,
            objEnums = enums,
        )
    }
}
