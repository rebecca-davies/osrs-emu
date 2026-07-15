package emu.server.world.network

import kotlin.math.abs
import kotlin.math.max

/** Zone-indexed immutable view shared by every observer during one information phase. */
internal class PlayerInfoView(players: List<PlayerInfoSnapshot>) {
    private val byIndex = arrayOfNulls<PlayerInfoSnapshot>(PLAYER_SLOTS)
    private val zones = HashMap<Int, MutableList<PlayerInfoSnapshot>>()

    init {
        for (player in players) {
            byIndex[player.index] = player
            zones.getOrPut(zoneKey(player)) { mutableListOf() } += player
        }
        zones.values.forEach { it.sortBy(PlayerInfoSnapshot::index) }
    }

    operator fun get(index: Int): PlayerInfoSnapshot? = byIndex[index]

    fun isVisible(observer: PlayerInfoSnapshot, target: PlayerInfoSnapshot): Boolean =
        observer.position.plane == target.position.plane &&
            max(
                abs(observer.position.x - target.position.x),
                abs(observer.position.y - target.position.y),
            ) <= VIEW_DISTANCE

    fun additions(
        observer: PlayerInfoSnapshot,
        tracked: BooleanArray,
        limit: Int,
    ): List<PlayerInfoSnapshot> {
        if (limit <= 0) return emptyList()
        val result = ArrayList<PlayerInfoSnapshot>(limit)
        val zoneX = observer.position.x shr ZONE_SHIFT
        val zoneY = observer.position.y shr ZONE_SHIFT
        for ((deltaX, deltaY) in ZONE_OFFSETS) {
            val key = zoneKey(observer.position.plane, zoneX + deltaX, zoneY + deltaY)
            for (target in zones[key].orEmpty()) {
                if (target.index == observer.index || tracked[target.index] || !isVisible(observer, target)) continue
                result += target
                if (result.size == limit) return result
            }
        }
        return result
    }

    private fun zoneKey(snapshot: PlayerInfoSnapshot): Int =
        zoneKey(snapshot.position.plane, snapshot.position.x shr ZONE_SHIFT, snapshot.position.y shr ZONE_SHIFT)

    private fun zoneKey(plane: Int, zoneX: Int, zoneY: Int): Int =
        (plane shl PLANE_SHIFT) or (zoneX shl ZONE_KEY_SHIFT) or zoneY

    private companion object {
        const val PLAYER_SLOTS = 2_048
        const val VIEW_DISTANCE = 15
        const val ZONE_SHIFT = 3
        const val ZONE_RADIUS = 2
        const val ZONE_KEY_SHIFT = 11
        const val PLANE_SHIFT = 22
        val ZONE_OFFSETS =
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
