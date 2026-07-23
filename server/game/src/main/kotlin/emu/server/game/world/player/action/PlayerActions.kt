package emu.server.game.world.player.action

import emu.game.action.PlayerAction
import emu.game.chat.ChatFilterInput
import emu.game.chat.ChatInput
import emu.game.chat.PublicChatInput
import emu.game.command.PlayerCommandInput
import emu.game.loc.LocOpInput
import emu.game.map.GameMap
import emu.game.map.Tile
import emu.game.npc.NpcOpInput
import emu.game.npc.NpcUid
import emu.game.player.Player
import emu.game.player.interaction.PlayerInteraction
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.input.TileInput
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import emu.persistence.chat.ChatChannel
import emu.server.game.world.player.command.PlayerCommandRepository
import emu.server.game.world.player.interaction.NpcInteractionTargetResolver
import java.time.Clock

/** Applies staged client actions to one player during the authoritative input phase. */
class PlayerActions(
    private val map: GameMap,
    private val npcTargets: NpcInteractionTargetResolver,
    private val scripts: PlayerScriptRunner,
    private val commands: PlayerCommandRepository,
    private val chatAudit: ChatAuditSink,
    private val clock: Clock = Clock.systemUTC(),
) {
    internal fun apply(
        player: Player,
        action: PlayerAction,
    ) {
        when (action) {
            is PlayerAction.Route -> player.applyRoute(action)
            is PlayerAction.Button -> player.applyButton(action.click)
            is PlayerAction.Chat -> player.applyChat(action.input)
            is PlayerAction.Command -> player.applyCommand(action.input)
            is PlayerAction.LocOp -> player.applyLocOp(action.input)
            is PlayerAction.NpcOp -> player.applyNpcOp(action.input)
            is PlayerAction.ResumeCountDialog -> scripts.resumeInput(player, action.input)
            is PlayerAction.ResumeObjDialog -> scripts.resumeInput(player, action.input)
            PlayerAction.CloseModal -> player.requestModalClose()
            PlayerAction.IdleLogout -> player.requestIdleLogout()
        }
    }

    private fun Player.applyNpcOp(input: NpcOpInput) {
        if (!interfaces.hasModal() && !canAccess()) return
        clearPendingAction()
        if (!canAccess()) return
        val target = npcTargets.resolve(this, input) ?: return
        val npc = target.npc
        val temporaryRun = temporaryRun(input.controlKey)
        beginInteraction(
            PlayerInteraction.NpcOp(
                target = NpcUid(npc.index, npc.uid),
                effectiveType = target.effectiveType.id,
                effectiveSize = target.effectiveType.size,
                option = input.option,
                subOption = input.subOption,
                temporaryRun = temporaryRun,
                mapInstance = mapInstance,
            ),
        )
        if (!map.canReachEntity(movement.position, npc.position, target.effectiveType.size)) {
            pathToEntity(npc.position, target.effectiveType.size, temporaryRun)
        }
    }

    private fun Player.applyLocOp(input: LocOpInput) {
        if (!interfaces.hasModal() && !canAccess()) return
        clearPendingAction()
        if (!canAccess()) return
        val tile = Tile(input.x, input.z, movement.position.plane)
        if (!buildArea.contains(tile)) return
        val loc = map.findLoc(input.type, tile) ?: return
        if (!loc.supports(input.option, input.subOption)) return
        beginInteraction(PlayerInteraction.LocOp(loc, input.option, input.subOption, mapInstance))
        if (!map.canReachLoc(movement.position, loc)) {
            pathTo(loc, temporaryRun(input.controlKey))
        }
    }

    private fun Player.applyRoute(action: PlayerAction.Route) {
        val destination = Tile(action.x, action.y, movement.position.plane)
        if (scripts.resumeInput(this, TileInput(destination))) return
        clearInteraction()
        walkTo(destination, temporaryRun(action.invertRun))
    }

    private fun Player.applyButton(click: ButtonClick) {
        if (!interfaces.isVisible(click.component)) return
        scripts.trigger(
            this,
            ServerTriggerType.IF_BUTTON,
            subject = click.packedComponent,
            lastButton = click,
        )
    }

    private fun Player.applyChat(input: ChatInput) {
        when (input) {
            is ChatFilterInput -> applyChatFilters(input)
            is PublicChatInput -> publishChat(input)
        }
    }

    private fun Player.applyChatFilters(input: ChatFilterInput) {
        if (input.publicFilter !in 0..3 || input.privateFilter !in 0..2 || input.tradeFilter !in 0..2) return
        chatFilters.update(input.publicFilter, input.privateFilter, input.tradeFilter)
    }

    private fun Player.publishChat(input: PublicChatInput) {
        if (publicChat != null) return
        val auditMessage = ChatAuditMessage(id, ChatChannel.PUBLIC, input.text, clock.instant())
        if (!chatAudit.submit(auditMessage)) return
        publishPublicChat(input)
    }

    private fun Player.applyCommand(input: PlayerCommandInput) {
        commands.execute(input.text, this)?.let(::messageGame)
    }

    private fun Player.temporaryRun(invert: Boolean): Boolean? =
        if (invert) !movement.runEnabled else null
}
