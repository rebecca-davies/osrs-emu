package emu.server.session

/** Privilege established during account authentication. */
enum class AccountPrivilege {
    PLAYER,
    MODERATOR,
    ADMINISTRATOR,
}
