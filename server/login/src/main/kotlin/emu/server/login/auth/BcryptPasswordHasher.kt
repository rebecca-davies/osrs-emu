package emu.server.login.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/** Bcrypt password hasher. */
class BcryptPasswordHasher(private val config: BcryptConfig = BcryptConfig()) : PasswordHasher {
    override fun hash(password: CharArray): String = BCrypt.withDefaults().hashToString(config.cost, password)

    override fun verify(password: CharArray, encoded: String): Boolean =
        BCrypt.verifyer().verify(password, encoded).verified
}
