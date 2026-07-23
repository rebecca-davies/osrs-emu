package emu.server.game.world.player.interaction

import emu.game.map.GameMap
import emu.game.player.Player
import emu.game.player.interaction.PlayerInteraction
import emu.game.script.execution.PlayerScriptRunner

/** Dispatches one retained player interaction around the authoritative movement phase. */
class PlayerInteractionProcess(
    map: GameMap,
    scripts: PlayerScriptRunner,
    npcTargets: NpcInteractionTargetResolver,
) {
    private val locs = LocInteractionProcess(map, scripts)
    private val npcs = NpcInteractionProcess(map, scripts, npcTargets)

    /** Cancels stale targets, reroutes moving targets, or completes a reached interaction. */
    fun beforeMovement(player: Player) {
        when (val interaction = player.interaction) {
            is PlayerInteraction.LocOp -> locs.beforeMovement(player, interaction)
            is PlayerInteraction.NpcOp -> npcs.beforeMovement(player, interaction)
            null -> Unit
        }
    }

    /** Completes a target reached by this cycle or terminates an exhausted route. */
    fun afterMovement(player: Player) {
        when (val interaction = player.interaction) {
            is PlayerInteraction.LocOp -> locs.afterMovement(player, interaction)
            is PlayerInteraction.NpcOp -> npcs.afterMovement(player, interaction)
            null -> Unit
        }
    }
}
