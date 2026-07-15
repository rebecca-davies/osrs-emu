package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * Sets the world and plane used by subsequent player, NPC, and zone updates in the cycle.
 *
 * @param index world index; `0` is the root world.
 * @param activeLevel the active plane/level for the following updates.
 */
data class SetActiveWorld(val index: Int = ROOT_WORLD, val activeLevel: Int = 0) : OutgoingMessage {
    companion object {
        /** Root-world index since rev 237. */
        const val ROOT_WORLD = 0
    }
}
