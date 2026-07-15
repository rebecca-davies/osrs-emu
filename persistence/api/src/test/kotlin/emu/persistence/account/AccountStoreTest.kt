package emu.persistence.account

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountStoreTest {
    @Test
    fun `account storage exposes only authentication persistence`() {
        val operations =
            AccountStore::class.java.declaredMethods
                .map { it.name }
                .toSet()

        assertEquals(setOf("create", "findByUsername"), operations)
    }
}
