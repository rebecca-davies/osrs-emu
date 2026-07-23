package emu.server.game.world.player.interaction

import emu.game.loc.Loc
import emu.game.map.GameMap
import emu.game.player.Player
import emu.game.player.interaction.PlayerInteraction
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.ServerTriggerType

/** Revalidates and completes retained static-loc operations. */
internal class LocInteractionProcess(
    private val map: GameMap,
    private val scripts: PlayerScriptRunner,
) {
    fun beforeMovement(player: Player, interaction: PlayerInteraction.LocOp) {
        process(player, interaction, terminateUnreachable = false)
    }

    fun afterMovement(player: Player, interaction: PlayerInteraction.LocOp) {
        process(player, interaction, terminateUnreachable = true)
    }

    private fun process(
        player: Player,
        interaction: PlayerInteraction.LocOp,
        terminateUnreachable: Boolean,
    ) {
        val loc = resolve(player, interaction)
        if (loc == null) {
            cancel(player, interaction)
            return
        }
        if (map.canReachLoc(player.movement.position, loc)) {
            if (!player.canAccess()) return
            if (!player.completeInteraction(interaction)) return
            player.stopMoving()
            scripts.trigger(
                player,
                ServerTriggerType.OPLOC1,
                subject = loc.type,
                argument = loc,
            )
            return
        }
        if (!terminateUnreachable || !player.canAccess() || player.movement.hasRoute) return
        if (!player.completeInteraction(interaction)) return
        player.stopMoving()
        player.messageGame(CANNOT_REACH)
    }

    private fun resolve(player: Player, interaction: PlayerInteraction.LocOp): Loc? {
        if (player.mapInstance != interaction.mapInstance) return null
        val expected = interaction.target
        if (!map.isCurrent(expected)) return null
        return expected.takeIf { it.supports(interaction.option, interaction.subOption) }
    }

    private fun cancel(player: Player, interaction: PlayerInteraction.LocOp) {
        if (player.completeInteraction(interaction)) player.stopMoving()
    }

    private companion object {
        const val CANNOT_REACH = "I can't reach that!"
    }
}
