package emu.server.login.auth

/** One-way password hashing and verification boundary owned by login authentication. */
interface PasswordHasher {
    fun hash(password: CharArray): String

    fun verify(password: CharArray, encoded: String): Boolean
}
