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
    private val sharedZones = HashMap<Int, MutableList<Npc>>()
    private val privateInstances = HashMap<MapInstance, MutableList<Npc>>()

    init {
        for (npc in npcs) {
            byIndex[npc.index] = npc
            if (npc.mapInstance == MapInstance.SHARED) {
                sharedZones.getOrPut(zoneKey(npc.position)) { arrayListOf() } += npc
            } else {
                privateInstances.getOrPut(npc.mapInstance) { arrayListOf() } += npc
            }
        }
        sharedZones.values.forEach { it.sortBy(Npc::index) }
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
        return if (mapInstance == MapInstance.SHARED) {
            selectSharedAdditions(position, tracked, destination, limit)
        } else {
            val instanceNpcs = privateInstances[mapInstance] ?: return 0
            selectPrivateAdditions(position, mapInstance, instanceNpcs, tracked, destination, limit)
        }
    }

    private fun selectSharedAdditions(
        position: Tile,
        tracked: BooleanArray?,
        destination: IntArray,
        limit: Int,
    ): Int {
        val zoneX = position.x shr ZONE_SHIFT
        val zoneY = position.y shr ZONE_SHIFT
        var selected = 0
        for ((deltaX, deltaY) in ZONE_OFFSETS) {
            val key = zoneKey(position.plane, zoneX + deltaX, zoneY + deltaY)
            val npcs = sharedZones[key] ?: continue
            for (npc in npcs) {
                if (tracked?.get(npc.index) == true || !isVisible(position, MapInstance.SHARED, npc)) continue
                destination[selected++] = npc.index
                if (selected == limit) return selected
            }
        }
        return selected
    }

    private fun selectPrivateAdditions(
        position: Tile,
        mapInstance: MapInstance,
        instanceNpcs: List<Npc>,
        tracked: BooleanArray?,
        destination: IntArray,
        limit: Int,
    ): Int {
        val observerZoneX = position.x shr ZONE_SHIFT
        val observerZoneY = position.y shr ZONE_SHIFT
        var selected = 0
        for (npc in instanceNpcs) {
            if (tracked?.get(npc.index) == true || !isVisible(position, mapInstance, npc)) continue
            selected = insertAddition(destination, selected, limit, npc, observerZoneX, observerZoneY)
        }
        return selected
    }

    private fun insertAddition(
        destination: IntArray,
        selected: Int,
        limit: Int,
        candidate: Npc,
        observerZoneX: Int,
        observerZoneY: Int,
    ): Int {
        val candidateKey = additionKey(candidate, observerZoneX, observerZoneY)
        if (selected == limit) {
            val finalKey = additionKey(checkNotNull(byIndex[destination[selected - 1]]), observerZoneX, observerZoneY)
            if (candidateKey >= finalKey) return selected
        }
        var insertion = minOf(selected, limit - 1)
        while (insertion > 0) {
            val previous = checkNotNull(byIndex[destination[insertion - 1]])
            if (candidateKey >= additionKey(previous, observerZoneX, observerZoneY)) break
            destination[insertion] = previous.index
            insertion--
        }
        destination[insertion] = candidate.index
        return minOf(selected + 1, limit)
    }

    private fun additionKey(npc: Npc, observerZoneX: Int, observerZoneY: Int): Int {
        val deltaX = (npc.position.x shr ZONE_SHIFT) - observerZoneX
        val deltaY = (npc.position.y shr ZONE_SHIFT) - observerZoneY
        val rank = ZONE_RANKS[(deltaX + ZONE_RADIUS) * ZONE_DIAMETER + deltaY + ZONE_RADIUS]
        return (rank shl NPC_INDEX_BITS) or npc.index
    }

    companion object {
        val EMPTY = NpcInfoView(emptyList())

        private const val VIEW_DISTANCE = 15
        private const val ZONE_SHIFT = 3
        private const val ZONE_RADIUS = (VIEW_DISTANCE + (1 shl ZONE_SHIFT) - 1) shr ZONE_SHIFT
        private const val ZONE_DIAMETER = ZONE_RADIUS * 2 + 1
        private const val ZONE_KEY_SHIFT = 11
        private const val PLANE_SHIFT = 22
        private const val NPC_INDEX_BITS = 15
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
        private val ZONE_RANKS =
            IntArray(ZONE_DIAMETER * ZONE_DIAMETER).also { ranks ->
                ZONE_OFFSETS.forEachIndexed { rank, (x, y) ->
                    ranks[(x + ZONE_RADIUS) * ZONE_DIAMETER + y + ZONE_RADIUS] = rank
                }
            }

        private fun zoneKey(position: Tile): Int =
            zoneKey(position.plane, position.x shr ZONE_SHIFT, position.y shr ZONE_SHIFT)

        private fun zoneKey(plane: Int, zoneX: Int, zoneY: Int): Int =
            (plane shl PLANE_SHIFT) or (zoneX shl ZONE_KEY_SHIFT) or zoneY
    }
}
