package emu.server.login.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BcryptPasswordHasherTest {
    private val passwords = BcryptPasswordHasher(BcryptConfig(cost = 4))

    @Test
    fun `bcrypt verifies only the original password`() {
        val hash = passwords.hash("hunter2".toCharArray())

        assertTrue(passwords.verify("hunter2".toCharArray(), hash))
        assertFalse(passwords.verify("not-hunter2".toCharArray(), hash))
    }

    @Test
    fun `bcrypt generates a new salt for each hash`() {
        val first = passwords.hash("same password".toCharArray())
        val second = passwords.hash("same password".toCharArray())

        assertNotEquals(first, second)
    }
}
