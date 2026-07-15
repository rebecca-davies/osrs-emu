package emu.server.world.runtime

/** Stable world-system identity used for validation, profiling and diagnostics. */
@JvmInline
value class WorldSystemId(val value: String) {
    init {
        require(value.isNotBlank()) { "world system id must not be blank" }
    }

    override fun toString(): String = value
}
