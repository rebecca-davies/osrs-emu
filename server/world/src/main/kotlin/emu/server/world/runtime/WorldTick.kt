package emu.server.world.runtime

/** Monotonic authoritative world-cycle number. */
@JvmInline
value class WorldTick(val value: Long) {
    init {
        require(value >= 0) { "world tick must not be negative" }
    }
}
