package emu.game.player.interaction

import emu.game.loc.Loc
import emu.game.map.MapInstance
import emu.game.npc.NpcType
import emu.game.npc.NpcUid

/** Authoritative pathing target retained while a player approaches gameplay content. */
sealed interface PlayerInteraction {
    val mapInstance: MapInstance

    /** One cache-backed loc operation that must be revalidated at interaction range. */
    data class LocOp(
        val target: Loc,
        val option: Int,
        val subOption: Int,
        override val mapInstance: MapInstance,
    ) : PlayerInteraction

    /** One stable NPC operation retained while its player-specific target remains valid. */
    data class NpcOp(
        val target: NpcUid,
        val effectiveType: Int,
        val effectiveSize: Int,
        val option: Int,
        val subOption: Int,
        val temporaryRun: Boolean?,
        override val mapInstance: MapInstance,
    ) : PlayerInteraction {
        init {
            require(effectiveType in 0..NpcType.MAX_ID) {
                "effective NPC type must fit the NPC information field"
            }
            require(effectiveSize in 1..0xFF) { "effective NPC size must fit an unsigned byte" }
            require(option in 1..5) { "NPC option must be in 1..5" }
            require(subOption in 0..0xFF) { "NPC sub-option must fit an unsigned byte" }
        }
    }
}
