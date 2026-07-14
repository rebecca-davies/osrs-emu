package emu.persistence

import at.favre.lib.crypto.bcrypt.BCrypt

/** Bcrypt password hashing and constant-time verification. */
class PasswordHasher(private val cost: Int = DEFAULT_COST) {
    init {
        require(cost in MIN_COST..MAX_COST) { "bcrypt cost must be in $MIN_COST..$MAX_COST" }
    }

    fun hash(password: CharArray): String = BCrypt.withDefaults().hashToString(cost, password)

    fun verify(password: CharArray, encoded: String): Boolean =
        BCrypt.verifyer().verify(password, encoded).verified

    private companion object {
        const val DEFAULT_COST = 12
        const val MIN_COST = 4
        const val MAX_COST = 31
    }
}
