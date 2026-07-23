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

    init {
        config.editorRoster.entries.forEach { editorNpc ->
            types[editorNpc.type]?.let { cacheNpc ->
                require(cacheNpc.name.equals(editorNpc.displayName, ignoreCase = true)) {
                    "Inferno editor NPC ${editorNpc.type} is named '${editorNpc.displayName}', " +
                        "but the cache names it '${cacheNpc.name}'"
                }
            }
        }
    }

    /** Whether [player] currently owns and occupies an active Inferno simulation. */
    fun isActive(player: Player): Boolean = activeSimulation(player) != null

    /** Selects one roster entry for the player's next placement. */
    fun selectNpcAt(player: Player, index: Int): InfernoNpcSelection {
        val simulation = activeSimulation(player) ?: return InfernoNpcSelection.NotInArena
        val editorNpc = config.editorRoster[index] ?: return InfernoNpcSelection.UnknownType
        val npcType = types[editorNpc.type] ?: return InfernoNpcSelection.UnknownType
        simulation.selectedNpcIndex = index
        return InfernoNpcSelection.Selected(npcType)
    }

    /** Resets private free-mode state and returns the player to the shared beta hub. */
    fun enterHub(player: Player) {
        release(player)
        npcs.remove(MapInstance.privateTo(player.id))
        player.teleportTo(config.clanWarsArrival, MapInstance.SHARED)
    }

    /** Starts one empty, paused Inferno instance owned by [player]. */
    fun enter(player: Player): InfernoEditorState {
        val instance = MapInstance.privateTo(player.id)
        simulations[player.index]?.let { npcs.remove(it.instance) }
        npcs.remove(instance)
        simulations[player.index] =
            InfernoSimulation(
                ownerId = player.id,
                instance = instance,
                paused = true,
                selectedNpcIndex = 0,
            )
        player.teleportTo(config.arenaArrival, instance)
        return checkNotNull(editorState(player))
    }

    /** Releases private simulation resources without changing the player's position. */
    fun release(player: Player): Int {
        val simulation = simulations[player.index] ?: return 0
        if (simulation.ownerId != player.id) return 0
        simulations[player.index] = null
        return npcs.remove(simulation.instance)
    }

    /** Current immutable editor projection, or null outside the owned simulation. */
    fun editorState(player: Player): InfernoEditorState? {
        val simulation = activeSimulation(player) ?: return null
        return simulation.toEditorState()
    }

    /** Removes all NPCs, pauses, and returns the player to the arena start tile. */
    fun reset(player: Player): InfernoEditorState? {
        val simulation = activeSimulation(player) ?: return null
        npcs.remove(simulation.instance)
        simulation.paused = true
        simulation.selectedNpcIndex = 0
        player.teleportTo(config.arenaArrival, simulation.instance)
        return simulation.toEditorState()
    }

    /** Places the NPC currently selected in the player's editor. */
    fun placeSelected(player: Player, position: Tile): InfernoNpcPlacement {
        val simulation = activeSimulation(player) ?: return InfernoNpcPlacement.NOT_IN_ARENA
        val editorNpc = config.editorRoster[simulation.selectedNpcIndex]
            ?: return InfernoNpcPlacement.NOT_IN_ARENA
        val npcType = types[editorNpc.type] ?: return InfernoNpcPlacement.UNKNOWN_TYPE
        return place(player, npcType, position)
    }

    private fun place(
        player: Player,
        npcType: NpcType,
        position: Tile,
    ): InfernoNpcPlacement {
        val simulation = activeSimulation(player) ?: return InfernoNpcPlacement.NOT_IN_ARENA
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

    private fun InfernoSimulation.toEditorState(): InfernoEditorState =
        InfernoEditorState(
            selectedNpcIndex = selectedNpcIndex,
            selectedNpc = checkNotNull(config.editorRoster[selectedNpcIndex]),
            paused = paused,
            npcCount = npcs.count(instance),
            maxNpcs = config.maxNpcs,
        )
}

/** Immutable state rendered by the Inferno free-mode editor. */
data class InfernoEditorState(
    val selectedNpcIndex: Int,
    val selectedNpc: InfernoEditorNpc,
    val paused: Boolean,
    val npcCount: Int,
    val maxNpcs: Int,
)

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
    UNKNOWN_TYPE,
}

/** Pause state authorized for one character and private Inferno instance. */
private data class InfernoSimulation(
    val ownerId: Long,
    val instance: MapInstance,
    var paused: Boolean,
    var selectedNpcIndex: Int,
)
