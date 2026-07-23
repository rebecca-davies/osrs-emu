package emu.server.game.world.npc

import emu.cache.def.EntityOps
import emu.cache.def.NpcDefinition
import emu.cache.def.VarTransform
import emu.cache.def.VarbitDefinition
import emu.game.varp.PlayerVariable
import emu.game.varp.PlayerVariableValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `cache morphs and conditional operations retain their player variable semantics`() {
        val catalog =
            CacheNpcCatalog(
                definitions =
                    listOf(
                        NpcDefinition(
                            1,
                            name = "Morphed NPC",
                            varTransform =
                                VarTransform(
                                    varbitId = 7,
                                    varpId = -1,
                                    configChangeDest = listOf(2),
                                    trailingVar = 3,
                                ),
                        ),
                        NpcDefinition(
                            2,
                            name = "Visible NPC",
                            ops =
                                EntityOps(
                                    conditionalOps =
                                        listOf(
                                            EntityOps.ConditionalOp(1, 0xFFFF, 7, 0, 0, "Talk-to"),
                                        ),
                                ),
                        ),
                        NpcDefinition(3, name = "Hidden NPC"),
                    ),
                varbits = listOf(VarbitDefinition(7, baseVar = 10, bits = 0..0)),
            )
        val base = requireNotNull(catalog[1])
        val zero = values(0)
        val one = values(1)

        val visible = requireNotNull(catalog.resolve(base, zero))
        assertEquals(2, visible.id)
        assertTrue(visible.operations.supports(option = 2, subOption = 0, variables = zero))
        assertEquals(3, requireNotNull(catalog.resolve(base, one)).id)
        assertFalse(visible.operations.supports(option = 2, subOption = 0, variables = one))
    }

    @Test
    fun `empty and hidden cache operation text is not authoritative`() {
        val type =
            requireNotNull(
                CacheNpcCatalog(
                    listOf(
                        NpcDefinition(
                            1,
                            name = "NPC",
                            ops =
                                EntityOps(
                                    ops = mapOf(0 to "", 1 to "Talk-to"),
                                    subOps =
                                        listOf(
                                            EntityOps.SubOp(1, 1, "Hidden"),
                                            EntityOps.SubOp(1, 2, "Trade"),
                                        ),
                                ),
                        ),
                    ),
                )[1],
            )
        val variables = values(0)

        assertFalse(type.operations.supports(option = 1, subOption = 0, variables))
        assertTrue(type.operations.supports(option = 2, subOption = 0, variables))
        assertFalse(type.operations.supports(option = 2, subOption = 1, variables))
        assertTrue(type.operations.supports(option = 2, subOption = 2, variables))
    }

    private fun values(value: Int): PlayerVariableValues =
        PlayerVariableValues { variable ->
            if (variable is PlayerVariable.Varbit && variable.id == 7) value else 0
        }
}
