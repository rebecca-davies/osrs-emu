package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * SET_ACTIVE_WORLD (opcode 47) — sets the client's active-world context for the packets that follow
 * in the same cycle (player info, npc info, zone updates). Since rev 237 the top-level "root" world
 * is index `0`; dynamic (world-entity) worlds use their own index. [activeLevel] is the plane the
 * following updates apply on.
 *
 * rsmod (`RspCycle.flush`) and the rsprot integration guide both send this as the **first** packet
 * of every post-tick flush, before PLAYER_INFO. The rev-235+ world-entity system processes player
 * and npc info relative to the active world, so a freshly logged-in client that never receives it
 * has no valid world context and drops the connection immediately after reaching the game screen.
 *
 * @param index world index; `0` = the root world (the milestone-5 default).
 * @param activeLevel the active plane/level for the following updates.
 */
data class SetActiveWorld(val index: Int = ROOT_WORLD, val activeLevel: Int = 0) : OutgoingMessage {
    companion object {
        /** The root/top-level world index (rev 237+). */
        const val ROOT_WORLD = 0
    }
}
