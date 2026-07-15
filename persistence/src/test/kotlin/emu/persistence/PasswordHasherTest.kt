package emu.persistence

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    private val passwords = PasswordHasher(cost = 4)

    @Test
    fun `bcrypt hash verifies the original password and rejects another`() {
        val hash = passwords.hash("hunter2".toCharArray())

        assertTrue(passwords.verify("hunter2".toCharArray(), hash))
        assertFalse(passwords.verify("not-hunter2".toCharArray(), hash))
    }

    @Test
    fun `bcrypt uses a random salt for each hash`() {
        val first = passwords.hash("same password".toCharArray())
        val second = passwords.hash("same password".toCharArray())

        assertNotEquals(first, second)
    }
}
