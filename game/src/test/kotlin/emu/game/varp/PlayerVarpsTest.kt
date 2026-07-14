package emu.game.varp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlayerVarpsTest {
    private val run = VarpType(173, scope = VarpScope.PERMANENT)
    private val displayNameBase = VarpType(1737, transmit = VarpTransmit.ON_CHANGE)
    private val hasDisplayName = VarbitType(8119, displayNameBase, 31..31)
    private val catalog = VarpCatalog(run, displayNameBase)

    @Test fun `permanent defaults and saved account values are included in the login sync`() {
        val defaults = PlayerVarps(catalog)
        val saved = PlayerVarps(catalog, mapOf(run.id to 0))

        assertEquals(0, defaults[run])
        assertEquals(0, saved[run])
        assertEquals(
            listOf(VarpValue(173, 0), VarpValue(1737, 0)),
            saved.loginSync(),
        )
    }

    @Test fun `a changed permanent varp is synced on diff and dirty until write behind`() {
        val vars = PlayerVarps(catalog)
        vars.markClientSynchronized()

        vars[run] = 1

        assertEquals(listOf(VarpValue(run.id, 1)), vars.drainClientUpdates())
        assertEquals(mapOf(run.id to 1), vars.dirtyPersistentValues())
        assertTrue(vars.drainClientUpdates().isEmpty())
    }

    @Test fun `changing back to the loaded value removes persistence dirtiness`() {
        val vars = PlayerVarps(catalog)
        vars.markClientSynchronized()

        vars[run] = 1
        vars[run] = 0

        assertTrue(vars.dirtyPersistentValues().isEmpty())
    }

    @Test fun `varbits pack safely into their base varp without becoming account data`() {
        val vars = PlayerVarps(catalog)

        vars[hasDisplayName] = 1

        assertEquals(1, vars[hasDisplayName])
        assertEquals(Int.MIN_VALUE, vars[displayNameBase])
        assertTrue(vars.dirtyPersistentValues().isEmpty())
        assertFailsWith<IllegalArgumentException> { vars[hasDisplayName] = 2 }
    }
}
