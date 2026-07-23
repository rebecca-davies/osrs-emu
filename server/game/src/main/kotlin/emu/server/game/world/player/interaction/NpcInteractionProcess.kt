package emu.server.game.world.player.interaction

import emu.game.map.GameMap
import emu.game.npc.NpcOpTarget
import emu.game.player.Player
import emu.game.player.interaction.PlayerInteraction
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.ServerTriggerType

/** Revalidates, reroutes, and completes retained NPC operations. */
internal class NpcInteractionProcess(
    private val map: GameMap,
    private val scripts: PlayerScriptRunner,
    private val targets: NpcInteractionTargetResolver,
) {
    fun beforeMovement(player: Player, interaction: PlayerInteraction.NpcOp) {
        process(player, interaction, reroute = true, terminateUnreachable = false)
    }

    fun afterMovement(player: Player, interaction: PlayerInteraction.NpcOp) {
        process(player, interaction, reroute = false, terminateUnreachable = true)
    }

    private fun process(
        player: Player,
        interaction: PlayerInteraction.NpcOp,
        reroute: Boolean,
        terminateUnreachable: Boolean,
    ) {
        val npc = targets.resolve(player, interaction)
        if (npc == null) {
            cancel(player, interaction)
            return
        }
        val targetPosition = npc.position
        if (map.canReachEntity(player.movement.position, targetPosition, interaction.effectiveSize)) {
            if (!player.canAccess()) return
            if (!player.completeInteraction(interaction)) return
            player.stopMoving()
            scripts.trigger(
                player,
                ServerTriggerType.npcOperation(interaction.option),
                subject = interaction.effectiveType,
                argument = NpcOpTarget(interaction.target, interaction.subOption),
            )
            return
        }
        if (reroute && !player.movement.isRoutedTo(targetPosition, interaction.effectiveSize)) {
            player.pathToEntity(targetPosition, interaction.effectiveSize, interaction.temporaryRun)
        }
        if (!terminateUnreachable || !player.canAccess() || player.movement.hasRoute) return
        if (!player.completeInteraction(interaction)) return
        player.stopMoving()
        player.messageGame(CANNOT_REACH)
    }

    private fun cancel(player: Player, interaction: PlayerInteraction.NpcOp) {
        if (player.completeInteraction(interaction)) player.stopMoving()
    }

    private companion object {
        const val CANNOT_REACH = "I can't reach that!"
    }
}
