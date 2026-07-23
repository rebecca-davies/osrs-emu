package emu.game.script.trigger

/** Jagex server trigger identities used to index Kotlin content scripts. */
enum class ServerTriggerType(val id: Int) {
    OPNPC1(10),
    OPNPC2(11),
    OPNPC3(12),
    OPNPC4(13),
    OPNPC5(14),
    OPLOC1(66),
    IF_BUTTON(147),
    IF_CLOSE(148),
    LOGIN(157),
    LOGOUT(158),

    ;

    companion object {
        /** Returns the Jagex trigger for a numbered NPC menu operation. */
        fun npcOperation(option: Int): ServerTriggerType =
            when (option) {
                1 -> OPNPC1
                2 -> OPNPC2
                3 -> OPNPC3
                4 -> OPNPC4
                5 -> OPNPC5
                else -> throw IllegalArgumentException("NPC option must be in 1..5")
            }
    }
}
