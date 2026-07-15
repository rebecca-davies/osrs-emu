package emu.server.game.world

/** Owns the bounded set of non-zero rev-239 player-array indexes. */
internal class PlayerIndexAllocator(maxPlayerIndex: Int) {
    init {
        require(maxPlayerIndex > 0) { "maximum player index must be positive" }
    }

    private val allocated = BooleanArray(maxPlayerIndex + 1)

    /** Reserves the lowest available player index, or returns null when every slot is in use. */
    fun allocate(): Int? {
        for (playerIndex in 1 until allocated.size) {
            if (!allocated[playerIndex]) {
                allocated[playerIndex] = true
                return playerIndex
            }
        }
        return null
    }

    fun release(playerIndex: Int) {
        check(playerIndex in 1 until allocated.size && allocated[playerIndex]) {
            "player index $playerIndex is not allocated"
        }
        allocated[playerIndex] = false
    }
}
