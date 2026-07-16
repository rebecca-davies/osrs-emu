package emu.server.session.account

/** Stable database identity for one Jagex account and its player character. */
@JvmInline
value class AccountId(val value: Long) {
    init {
        require(value > 0) { "account id must be positive" }
    }
}

/** Privilege established during account authentication. */
enum class AccountPrivilege(val level: Int) {
    PLAYER(0),
    MODERATOR(1),
    ADMINISTRATOR(2),
}

/** Account identity established by login without loading mutable character state. */
data class AuthenticatedAccount(
    val accountId: AccountId,
    val privilege: AccountPrivilege,
)
