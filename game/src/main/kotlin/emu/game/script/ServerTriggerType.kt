package emu.game.script

/** Jagex server trigger identities used to index Kotlin content scripts. */
enum class ServerTriggerType(val id: Int) {
    IF_BUTTON(147),
    IF_CLOSE(148),
    LOGIN(157),
    LOGOUT(158),
}
