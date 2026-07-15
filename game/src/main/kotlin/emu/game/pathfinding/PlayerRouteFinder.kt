package emu.game.pathfinding

/** Narrow route-search capability used while applying a player's queued route action. */
interface PlayerRouteFinder {
    fun routeTo(
        movement: PlayerMovement,
        destination: Tile,
        temporaryRun: Boolean? = null,
    ): PathRoute
}
