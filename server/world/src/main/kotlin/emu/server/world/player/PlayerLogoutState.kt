package emu.server.world.player

/** A player's world-thread-owned logout request. */
internal class PlayerLogoutState {
    var requested: Boolean = false
        private set

    fun request() {
        requested = true
    }
}
