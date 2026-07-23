package emu.server.game.world.npc

import emu.cache.def.CacheNpcDefinitionCatalog
import emu.cache.def.CacheVarbitDefinitionCatalog
import emu.cache.store.FlatFileStore
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InfernoRosterRealCacheTest {
    @Test
    fun `revision 239 resolves every configured Inferno editor NPC`() {
        val cache =
            listOf(File("../../cache-data"), File("../../../osrsemu/cache-data"))
                .firstOrNull { File(it, "cache/255/2.dat").isFile }
                ?: return
        val store = FlatFileStore(cache)
        val types =
            CacheNpcCatalog(
                CacheNpcDefinitionCatalog(store).definitions,
                CacheVarbitDefinitionCatalog(store).definitions,
            )

        for (configured in InfernoFreeModeCatalog.load().editorRoster.entries) {
            val cached = assertNotNull(types[configured.type], "missing NPC type ${configured.type}")
            assertEquals(configured.displayName, cached.name, "NPC type ${configured.type}")
        }
    }
}
