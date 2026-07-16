package emu.persistence.account

/** Stable persisted privilege level. */
enum class AccountRank(val id: Int) {
    PLAYER(0),
    MODERATOR(1),
    ADMINISTRATOR(2),
}
