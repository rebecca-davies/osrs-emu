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
    operator fun get(type: Int): NpcType? = types[type]

    /** Resets private free-mode state and returns the player to the shared beta hub. */
    fun enterHub(player: Player) {
        npcs.remove(MapInstance.privateTo(player.id))
        player.teleportTo(config.clanWarsArrival, MapInstance.SHARED)
    }

    /** Starts one empty, paused Inferno instance owned by [player]. */
    fun enter(player: Player) {
        val instance = MapInstance.privateTo(player.id)
        npcs.remove(instance)
        player.teleportTo(config.arenaArrival, instance)
    }

    /** Places one cache-backed NPC and pauses all NPCs in the player's instance. */
    fun place(player: Player, type: Int, position: Tile): InfernoNpcPlacement {
        val instance = MapInstance.privateTo(player.id)
        if (player.mapInstance != instance) return InfernoNpcPlacement.NOT_IN_ARENA
        val npcType = types[type] ?: return InfernoNpcPlacement.UNKNOWN_TYPE
        if (!config.arenaBounds.contains(position, npcType.size)) {
            return InfernoNpcPlacement.OUTSIDE_ARENA
        }
        if (npcs.count(instance) >= config.maxNpcs) return InfernoNpcPlacement.INSTANCE_CAPACITY
        if (!map.canOccupy(position, npcType.size)) return InfernoNpcPlacement.BLOCKED
        if (overlapsPlayer(player, position, npcType.size) || npcs.intersects(instance, position, npcType.size)) {
            return InfernoNpcPlacement.OCCUPIED
        }
        npcs.pause(instance, paused = true)
        return if (npcs.add(npcType, position, instance, targetPlayerId = player.id, paused = true) == null) {
            InfernoNpcPlacement.WORLD_CAPACITY
        } else {
            InfernoNpcPlacement.PLACED
        }
    }

    /** Removes every NPC in the player's active private Inferno instance. */
    fun clear(player: Player): Int? =
        activeInstance(player)?.let(npcs::remove)

    /** Toggles every NPC in the player's active private Inferno instance. */
    fun togglePaused(player: Player): Boolean? =
        activeInstance(player)?.let(npcs::togglePaused)

    private fun activeInstance(player: Player): MapInstance? =
        MapInstance.privateTo(player.id).takeIf { it == player.mapInstance }

    private fun overlapsPlayer(player: Player, position: Tile, size: Int): Boolean {
        val playerTile = player.movement.position
        return playerTile.plane == position.plane &&
            playerTile.x in position.x until position.x + size &&
            playerTile.y in position.y until position.y + size
    }
}

/** Result of one server-authoritative free-mode NPC placement request. */
enum class InfernoNpcPlacement {
    PLACED,
    NOT_IN_ARENA,
    UNKNOWN_TYPE,
    OUTSIDE_ARENA,
    BLOCKED,
    OCCUPIED,
    INSTANCE_CAPACITY,
    WORLD_CAPACITY,
}
