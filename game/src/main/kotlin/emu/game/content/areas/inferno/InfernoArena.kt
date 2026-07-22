package emu.game.content.areas.inferno

import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.npc.NpcType
import emu.game.player.Player

/** Authoritative free-mode entry, NPC placement, and simulation controls. */
class InfernoArena(
    private val map: GameMap,
    private val types: NpcCatalog,
    private val npcs: NpcList,
    val config: InfernoFreeModeConfig,
) {
    private val simulations = arrayOfNulls<InfernoSimulation>(Player.MAX_CLIENT_INDEX + 1)

    /** Whether [player] currently owns and occupies an active Inferno simulation. */
    fun isActive(player: Player): Boolean = activeSimulation(player) != null

    /** Resolves an NPC selection only for a player inside their Inferno simulation. */
    fun selectNpc(player: Player, type: Int): InfernoNpcSelection {
        if (!isActive(player)) return InfernoNpcSelection.NotInArena
        val npcType = types[type] ?: return InfernoNpcSelection.UnknownType
        return InfernoNpcSelection.Selected(npcType)
    }

    /** Resets private free-mode state and returns the player to the shared beta hub. */
    fun enterHub(player: Player) {
        npcs.remove(MapInstance.privateTo(player.id))
        simulations[player.index] = null
        player.teleportTo(config.clanWarsArrival, MapInstance.SHARED)
    }

    /** Starts one empty, paused Inferno instance owned by [player]. */
    fun enter(player: Player) {
        val instance = MapInstance.privateTo(player.id)
        npcs.remove(instance)
        simulations[player.index] = InfernoSimulation(player.id, instance, paused = true)
        player.teleportTo(config.arenaArrival, instance)
    }

    /** Places one preflighted NPC and pauses the simulation when placement succeeds. */
    fun place(
        player: Player,
        selection: InfernoNpcSelection.Selected,
        position: Tile,
    ): InfernoNpcPlacement {
        val simulation = activeSimulation(player) ?: return InfernoNpcPlacement.NOT_IN_ARENA
        val npcType = selection.type
        if (!config.arenaBounds.contains(position, npcType.size)) {
            return InfernoNpcPlacement.OUTSIDE_ARENA
        }
        if (npcs.count(simulation.instance) >= config.maxNpcs) return InfernoNpcPlacement.INSTANCE_CAPACITY
        if (!map.canOccupy(position, npcType.size)) return InfernoNpcPlacement.BLOCKED
        if (
            overlapsPlayer(player, position, npcType.size) ||
            npcs.intersects(simulation.instance, position, npcType.size)
        ) {
            return InfernoNpcPlacement.OCCUPIED
        }
        if (npcs.add(npcType, position, simulation.instance, target = player, paused = true) == null) {
            return InfernoNpcPlacement.WORLD_CAPACITY
        }
        simulation.paused = true
        npcs.pause(simulation.instance, paused = true)
        return InfernoNpcPlacement.PLACED
    }

    /** Removes every NPC in the player's active private Inferno instance. */
    fun clear(player: Player): Int? {
        val simulation = activeSimulation(player) ?: return null
        simulation.paused = true
        return npcs.remove(simulation.instance)
    }

    /** Toggles the player's simulation even when its NPC list is empty. */
    fun togglePaused(player: Player): InfernoPauseResult {
        val simulation = activeSimulation(player) ?: return InfernoPauseResult.NOT_IN_ARENA
        simulation.paused = !simulation.paused
        npcs.pause(simulation.instance, simulation.paused)
        return if (simulation.paused) InfernoPauseResult.PAUSED else InfernoPauseResult.RESUMED
    }

    private fun activeSimulation(player: Player): InfernoSimulation? {
        val simulation = simulations[player.index] ?: return null
        return simulation.takeIf {
            it.ownerId == player.id && it.instance == player.mapInstance
        }
    }

    private fun overlapsPlayer(player: Player, position: Tile, size: Int): Boolean {
        val playerTile = player.movement.position
        return playerTile.plane == position.plane &&
            playerTile.x in position.x until position.x + size &&
            playerTile.y in position.y until position.y + size
    }
}

/** Cache-backed NPC selection accepted by the Inferno editor. */
sealed interface InfernoNpcSelection {
    class Selected internal constructor(val type: NpcType) : InfernoNpcSelection

    data object NotInArena : InfernoNpcSelection

    data object UnknownType : InfernoNpcSelection
}

/** Result of toggling one player's Inferno simulation. */
enum class InfernoPauseResult {
    PAUSED,
    RESUMED,
    NOT_IN_ARENA,
}

/** Result of one server-authoritative free-mode NPC placement request. */
enum class InfernoNpcPlacement {
    PLACED,
    NOT_IN_ARENA,
    OUTSIDE_ARENA,
    BLOCKED,
    OCCUPIED,
    INSTANCE_CAPACITY,
    WORLD_CAPACITY,
}

/** Pause state authorized for one character and private Inferno instance. */
private data class InfernoSimulation(
    val ownerId: Long,
    val instance: MapInstance,
    var paused: Boolean,
)
