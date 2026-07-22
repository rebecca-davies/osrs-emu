package emu.game.obj

/** Jagex worn-inventory positions in client slot order. */
enum class Wearpos(val slot: Int, val clientOnly: Boolean = false) {
    HAT(0),
    BACK(1),
    FRONT(2),
    RIGHT_HAND(3),
    TORSO(4),
    LEFT_HAND(5),
    ARMS(6, clientOnly = true),
    LEGS(7),
    HEAD(8, clientOnly = true),
    HANDS(9),
    FEET(10),
    JAW(11, clientOnly = true),
    RING(12),
    QUIVER(13),
    ;

    companion object {
        val APPEARANCE = entries.take(JAW.slot + 1)

        private val bySlot = entries.associateBy(Wearpos::slot)

        fun fromSlot(slot: Int?): Wearpos? = slot?.let(bySlot::get)
    }
}
