package emu.server.login.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountIdentityTest {
    @Test
    fun `identity is case insensitive while display name preserves capitalization`() {
        assertEquals(
            AccountIdentity(username = "rebecca bird", displayName = "Rebecca_Bird"),
            AccountIdentity.parse("  Rebecca_Bird  "),
        )
        assertEquals(
            AccountIdentity(username = "rebecca bird", displayName = "REBECCA BIRD"),
            AccountIdentity.parse("REBECCA BIRD"),
        )
        assertEquals(
            AccountIdentity(username = "rebecca bird", displayName = "Rebecca-Bird"),
            AccountIdentity.parse("Rebecca-Bird"),
        )
    }

    @Test
    fun `identity rejects blank too long and unsupported names`() {
        assertNull(AccountIdentity.parse("___"))
        assertNull(AccountIdentity.parse("thirteen chars"))
        assertNull(AccountIdentity.parse("name@example"))
    }
}
