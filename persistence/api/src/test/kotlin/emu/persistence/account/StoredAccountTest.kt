package emu.persistence.account

import kotlin.test.Test
import kotlin.test.assertFalse

class StoredAccountTest {
    @Test
    fun `string representation cannot expose a password hash`() {
        val hash = "secret-hash"
        val stored = StoredAccount(AccountRecord(1, "name", "Name", AccountRank.PLAYER), hash)

        assertFalse(hash in stored.toString())
        assertFalse("name" in stored.account.toString())
    }
}
