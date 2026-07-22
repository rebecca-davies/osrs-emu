package emu.game.map

/** Server-side identity separating entities that occupy the same cache-backed coordinates. */
@JvmInline
value class MapInstance private constructor(val id: Long) {
    companion object {
        /** Shared overworld seen by every ordinary player. */
        val SHARED = MapInstance(0)

        /** Creates the private map instance owned by one character. */
        fun privateTo(playerId: Long): MapInstance {
            require(playerId > 0) { "private map instance owner must be positive" }
            return MapInstance(playerId)
        }
    }
}
