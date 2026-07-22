package emu.game.player

/** Jagex staff privilege level attached to a live player. */
@JvmInline
value class StaffModLevel(val value: Int) {
    init {
        require(value in VALID_LEVELS) { "staff mod level must be in 0..2" }
    }

    companion object {
        private val VALID_LEVELS = 0..2
        val NONE = StaffModLevel(0)
    }
}
