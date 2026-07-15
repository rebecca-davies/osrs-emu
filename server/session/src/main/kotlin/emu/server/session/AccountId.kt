package emu.server.session

/** Stable database identity for one Jagex account and its player character. */
@JvmInline
value class AccountId(val value: Long) {
    init {
        require(value > 0) { "account id must be positive" }
    }
}
