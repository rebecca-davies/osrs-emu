package emu.game.npc

import emu.game.map.Direction
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.player.Player

/** Bounded slot-indexed NPC membership and private-instance lookup owned by one world. */
class NpcList(val capacity: Int = DEFAULT_CAPACITY) {
    private val slots: Array<Npc?>
    private val freeIndexes: IntArray
    private val ordered: ArrayList<Npc>
    private val byInstance = HashMap<MapInstance, ArrayList<Npc>>()
    private var freeCount: Int
    private var nextUid = 1L

    var size: Int = 0
        private set

    init {
        require(capacity in 1..DEFAULT_CAPACITY) { "NPC capacity must be in 1..$DEFAULT_CAPACITY" }
        slots = arrayOfNulls(capacity)
        freeIndexes = IntArray(capacity) { capacity - it - 1 }
        ordered = ArrayList(capacity)
        freeCount = capacity
    }

    /** Adds one NPC at a stable client index, or null when the world is full. */
    fun add(
        type: NpcType,
        position: Tile,
        mapInstance: MapInstance,
        orientation: Direction = Direction.SOUTH,
        target: Player? = null,
        paused: Boolean = false,
    ): Npc? {
        if (freeCount == 0) return null
        check(nextUid > 0) { "NPC uid space is exhausted" }
        val index = freeIndexes[--freeCount]
        val npc = Npc(index, nextUid++, type, position, mapInstance, orientation, target, paused)
        slots[index] = npc
        npc.listPosition = ordered.size
        ordered += npc
        val instanceNpcs = byInstance.getOrPut(mapInstance) { arrayListOf() }
        npc.instancePosition = instanceNpcs.size
        instanceNpcs += npc
        size++
        return npc
    }

    operator fun get(index: Int): Npc? = slots.getOrNull(index)

    /** Removes one NPC when it still owns its allocated client index. */
    fun remove(npc: Npc): Boolean {
        if (slots.getOrNull(npc.index) !== npc) return false
        npc.clearTarget()
        slots[npc.index] = null
        removeAtSwap(ordered, npc.listPosition) { moved, position -> moved.listPosition = position }
        val instanceNpcs = checkNotNull(byInstance[npc.mapInstance])
        removeAtSwap(instanceNpcs, npc.instancePosition) { moved, position -> moved.instancePosition = position }
        if (instanceNpcs.isEmpty()) byInstance.remove(npc.mapInstance)
        npc.listPosition = -1
        npc.instancePosition = -1
        freeIndexes[freeCount++] = npc.index
        size--
        return true
    }

    /** Removes every NPC in [mapInstance] and returns the number removed. */
    fun remove(mapInstance: MapInstance): Int {
        val instanceNpcs = byInstance[mapInstance] ?: return 0
        val count = instanceNpcs.size
        while (instanceNpcs.isNotEmpty()) remove(instanceNpcs.last())
        return count
    }

    fun count(mapInstance: MapInstance): Int = byInstance[mapInstance]?.size ?: 0

    /** Changes every NPC in an instance to the same paused state. */
    fun pause(mapInstance: MapInstance, paused: Boolean): Int {
        val instanceNpcs = byInstance[mapInstance] ?: return 0
        instanceNpcs.forEach { it.setPaused(paused) }
        return instanceNpcs.size
    }

    /** Whether an NPC other than [excluding] overlaps the requested footprint in [mapInstance]. */
    fun intersects(mapInstance: MapInstance, position: Tile, size: Int, excluding: Npc? = null): Boolean {
        val instanceNpcs = byInstance[mapInstance] ?: return false
        return instanceNpcs.any { npc ->
            npc !== excluding && npc.position.plane == position.plane &&
                overlaps(position.x, size, npc.position.x, npc.type.size) &&
                overlaps(position.y, size, npc.position.y, npc.type.size)
        }
    }

    /** Appends every active NPC without allocating a world-list copy. */
    fun collect(destination: MutableCollection<Npc>) {
        destination.addAll(ordered)
    }

    private fun removeAtSwap(list: ArrayList<Npc>, position: Int, update: (Npc, Int) -> Unit) {
        check(position in list.indices)
        val last = list.removeAt(list.lastIndex)
        if (position < list.size) {
            list[position] = last
            update(last, position)
        }
    }

    private fun overlaps(first: Int, firstSize: Int, second: Int, secondSize: Int): Boolean =
        first < second + secondSize && second < first + firstSize

    companion object {
        const val DEFAULT_CAPACITY = 32_767
    }
}
