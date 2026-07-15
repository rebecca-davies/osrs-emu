package emu.server.login.auth

private const val MINIMUM_BCRYPT_COST = 4
private const val MAXIMUM_BCRYPT_COST = 31

/** Bcrypt work factor used for newly created accounts. */
data class BcryptConfig(val cost: Int = 12) {
    init {
        require(cost in MINIMUM_BCRYPT_COST..MAXIMUM_BCRYPT_COST) {
            "bcrypt cost must be in $MINIMUM_BCRYPT_COST..$MAXIMUM_BCRYPT_COST"
        }
    }
}
