package emu.server.game.network.output.playerinfo

/** Maintains one observer's high-resolution player distance under crowd pressure. */
internal class PlayerInfoViewport {
    var distance: Int = PREFERRED_DISTANCE
        private set

    private var expansionCounter: Int = RESIZE_INTERVAL

    fun resize(highResolutionPlayers: Int) {
        if (highResolutionPlayers >= PREFERRED_PLAYER_COUNT) {
            if (distance > 0) distance--
            expansionCounter = 0
            return
        }
        if (++expansionCounter >= RESIZE_INTERVAL) {
            if (distance < PREFERRED_DISTANCE) {
                distance++
            } else {
                expansionCounter = 0
            }
        }
    }

    fun availableAdditions(highResolutionPlayers: Int): Int =
        (PREFERRED_PLAYER_COUNT - highResolutionPlayers).coerceAtLeast(0)

    companion object {
        const val PREFERRED_DISTANCE = 15
        private const val PREFERRED_PLAYER_COUNT = 250
        private const val RESIZE_INTERVAL = 10
    }
}
