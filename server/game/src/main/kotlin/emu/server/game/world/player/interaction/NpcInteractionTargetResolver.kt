package emu.server.game.world.player.interaction

import emu.game.npc.Npc
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.npc.NpcOpInput
import emu.game.npc.NpcType
import emu.game.npc.NpcUid
import emu.game.player.Player
import emu.game.player.interaction.PlayerInteraction
import emu.server.game.world.World
import kotlin.math.max

/** Resolves player-visible NPC operations and revalidates their stable world identities. */
class NpcInteractionTargetResolver internal constructor(
    private val npcs: NpcList,
    private val types: NpcCatalog,
    private val localUid: (Player, Int) -> NpcUid?,
) {
    internal fun resolve(player: Player, input: NpcOpInput): NpcInteractionTarget? {
        val uid = localUid(player, input.index) ?: return null
        val npc = resolveNpc(player, uid) ?: return null
        val effectiveType = types.resolve(npc.type, player.varps) ?: return null
        if (npc.distanceFrom(player, effectiveType.size) > MAX_NPC_OPERATION_DISTANCE) {
            return null
        }
        if (!effectiveType.operations.supports(input.option, input.subOption, player.varps)) {
            return null
        }
        return NpcInteractionTarget(npc, effectiveType)
    }

    internal fun resolve(
        player: Player,
        interaction: PlayerInteraction.NpcOp,
    ): Npc? {
        if (player.mapInstance != interaction.mapInstance) return null
        val npc = resolveNpc(player, interaction.target) ?: return null
        val effectiveType = types.resolve(npc.type, player.varps) ?: return null
        if (effectiveType.id != interaction.effectiveType) return null
        if (effectiveType.size != interaction.effectiveSize) return null
        if (
            !effectiveType.operations.supports(
                interaction.option,
                interaction.subOption,
                player.varps,
            )
        ) {
            return null
        }
        return npc
    }

    private fun resolveNpc(player: Player, uid: NpcUid): Npc? {
        val npc = npcs.resolve(uid) ?: return null
        if (npc.mapInstance != player.mapInstance || npc.position.plane != player.movement.position.plane) {
            return null
        }
        return npc
    }

    private fun Npc.distanceFrom(player: Player, size: Int): Int {
        val position = player.movement.position
        val maxX = this.position.x + size - 1
        val maxY = this.position.y + size - 1
        val deltaX =
            when {
                position.x < this.position.x -> this.position.x - position.x
                position.x > maxX -> position.x - maxX
                else -> 0
            }
        val deltaY =
            when {
                position.y < this.position.y -> this.position.y - position.y
                position.y > maxY -> position.y - maxY
                else -> 0
            }
        return max(deltaX, deltaY)
    }

    companion object {
        /** Uses the NPC identities retained by each world's connection-local information list. */
        fun usingWorld(
            world: World,
            npcs: NpcList,
            types: NpcCatalog,
        ): NpcInteractionTargetResolver =
            NpcInteractionTargetResolver(npcs, types, world::resolveLocalNpc)

        private const val MAX_NPC_OPERATION_DISTANCE = 15
    }
}

/** Authoritative live NPC and player-specific cache type for one operation attempt. */
internal data class NpcInteractionTarget(val npc: Npc, val effectiveType: NpcType)
