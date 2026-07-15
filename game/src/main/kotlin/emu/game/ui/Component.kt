package emu.game.ui

/** Packed Jagex interface and child-component identity. */
@JvmInline
value class Component(val packed: Int) {
    val interfaceId: Int
        get() = packed ushr 16

    val componentId: Int
        get() = packed and 0xFFFF

    companion object {
        fun of(interfaceId: Int, componentId: Int): Component {
            require(interfaceId in UNSIGNED_SHORT && componentId in UNSIGNED_SHORT) {
                "component halves must fit unsigned shorts"
            }
            return Component((interfaceId shl 16) or componentId)
        }

        private val UNSIGNED_SHORT = 0..0xFFFF
    }
}
