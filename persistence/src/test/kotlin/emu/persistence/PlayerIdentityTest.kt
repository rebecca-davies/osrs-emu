package emu.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerIdentityTest {
    @Test
    fun `identity is case insensitive while display name preserves capitalization`() {
        assertEquals(
            PlayerIdentity(username = "rebecca bird", displayName = "Rebecca_Bird"),
            PlayerIdentity.parse("  Rebecca_Bird  "),
        )
        assertEquals(
            PlayerIdentity(username = "rebecca bird", displayName = "REBECCA BIRD"),
            PlayerIdentity.parse("REBECCA BIRD"),
        )
        assertEquals(
            PlayerIdentity(username = "rebecca bird", displayName = "Rebecca-Bird"),
            PlayerIdentity.parse("Rebecca-Bird"),
        )
    }

    @Test
    fun `identity rejects blank too long and unsupported names`() {
        assertNull(PlayerIdentity.parse("___"))
        assertNull(PlayerIdentity.parse("thirteen chars"))
        assertNull(PlayerIdentity.parse("name@example"))
    }
}
