package emu.game.obj

/** One positive object quantity paired with the authoritative definition that governs it. */
data class ObjStack(val type: ObjType, val count: Int = 1) {
    init {
        require(count > 0) { "object count must be positive" }
    }

    /** Raw container value suitable for client synchronization. */
    fun toObj(): Obj = Obj(type.id, count)
}
