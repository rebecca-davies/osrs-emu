package emu.server.game.network.output.npcinfo

import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.Npc
import emu.game.npc.NpcList
import kotlin.math.abs
import kotlin.math.max

/** Zone-indexed NPC view shared by every observer during one information phase. */
internal class NpcInfoView(npcs: List<Npc>) {
    val isEmpty: Boolean = npcs.isEmpty()
    private val byIndex = arrayOfNulls<Npc>(NpcList.DEFAULT_CAPACITY)
    private val zones = HashMap<MapInstance, MutableMap<Int, MutableList<Npc>>>()

    init {
        for (npc in npcs) {
            byIndex[npc.index] = npc
            val instanceZones = zones.getOrPut(npc.mapInstance) { hashMapOf() }
            instanceZones.getOrPut(zoneKey(npc.position)) { mutableListOf() } += npc
        }
        zones.values.forEach { instance ->
            instance.values.forEach { it.sortBy(Npc::index) }
        }
    }

    operator fun get(index: Int): Npc? = byIndex.getOrNull(index)

    fun isVisible(position: Tile, mapInstance: MapInstance, npc: Npc): Boolean =
        mapInstance == npc.mapInstance && position.plane == npc.position.plane &&
            max(abs(position.x - npc.position.x), abs(position.y - npc.position.y)) <= VIEW_DISTANCE

    /** Fills [destination] with up to [limit] visible, untracked NPC indexes. */
    fun selectAdditions(
        position: Tile,
        mapInstance: MapInstance,
        tracked: BooleanArray?,
        destination: IntArray,
        limit: Int,
    ): Int {
        if (limit <= 0) return 0
        val instanceZones = zones[mapInstance] ?: return 0
        val zoneX = position.x shr ZONE_SHIFT
        val zoneY = position.y shr ZONE_SHIFT
        var selected = 0
        for ((deltaX, deltaY) in ZONE_OFFSETS) {
            val npcs = instanceZones[zoneKey(position.plane, zoneX + deltaX, zoneY + deltaY)] ?: continue
            for (npc in npcs) {
                if (tracked?.get(npc.index) == true || !isVisible(position, mapInstance, npc)) continue
                destination[selected++] = npc.index
                if (selected == limit) return selected
            }
        }
        return selected
    }

    private fun zoneKey(position: Tile): Int =
        zoneKey(position.plane, position.x shr ZONE_SHIFT, position.y shr ZONE_SHIFT)

    private fun zoneKey(plane: Int, zoneX: Int, zoneY: Int): Int =
        (plane shl PLANE_SHIFT) or (zoneX shl ZONE_KEY_SHIFT) or zoneY

    companion object {
        val EMPTY = NpcInfoView(emptyList())

        private const val VIEW_DISTANCE = 15
        private const val ZONE_SHIFT = 3
        private const val ZONE_RADIUS = (VIEW_DISTANCE + (1 shl ZONE_SHIFT) - 1) shr ZONE_SHIFT
        private const val ZONE_KEY_SHIFT = 11
        private const val PLANE_SHIFT = 22
        private val ZONE_OFFSETS =
            buildList {
                for (x in -ZONE_RADIUS..ZONE_RADIUS) {
                    for (y in -ZONE_RADIUS..ZONE_RADIUS) add(x to y)
                }
            }.sortedWith(
                compareBy<Pair<Int, Int>> { max(abs(it.first), abs(it.second)) }
                    .thenBy { abs(it.first) + abs(it.second) }
                    .thenBy(Pair<Int, Int>::first)
                    .thenBy(Pair<Int, Int>::second),
            )
    }
}
