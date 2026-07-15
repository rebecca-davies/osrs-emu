package emu.server.session

/** Immutable ISAAC seed material established by the login protocol. */
data class IsaacBootstrap(
    val seed0: Int,
    val seed1: Int,
    val seed2: Int,
    val seed3: Int,
) {
    fun toIntArray(): IntArray = intArrayOf(seed0, seed1, seed2, seed3)
}

fun isaacBootstrap(seeds: IntArray): IsaacBootstrap {
    require(seeds.size == 4) { "ISAAC bootstrap requires four seeds" }
    return IsaacBootstrap(seeds[0], seeds[1], seeds[2], seeds[3])
}
