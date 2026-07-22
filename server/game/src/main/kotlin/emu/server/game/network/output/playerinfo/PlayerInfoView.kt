package emu.server.game.network.output.playerinfo

import emu.game.map.MapInstance
import kotlin.math.abs
import kotlin.math.max

/** Zone-indexed immutable view shared by every observer during one information phase. */
internal class PlayerInfoView(players: List<PlayerInfoSnapshot>) {
    val playerCount: Int = players.size
    private val byIndex = arrayOfNulls<PlayerInfoSnapshot>(PLAYER_SLOTS)
    private val sharedZones = HashMap<Int, MutableList<PlayerInfoSnapshot>>()
    private val privateInstances = HashMap<MapInstance, MutableList<PlayerInfoSnapshot>>()

    init {
        for (player in players) {
            byIndex[player.index] = player
            if (player.mapInstance == MapInstance.SHARED) {
                sharedZones.getOrPut(zoneKey(player)) { arrayListOf() } += player
            } else {
                privateInstances.getOrPut(player.mapInstance) { arrayListOf() } += player
            }
        }
        sharedZones.values.forEach { it.sortBy(PlayerInfoSnapshot::index) }
        privateInstances.values.forEach { it.sortWith(PLAYER_ZONE_ORDER) }
    }

    operator fun get(index: Int): PlayerInfoSnapshot? = byIndex[index]

    fun isVisible(observer: PlayerInfoSnapshot, target: PlayerInfoSnapshot, distance: Int): Boolean =
        observer.mapInstance == target.mapInstance &&
            observer.position.plane == target.position.plane &&
            max(
                abs(observer.position.x - target.position.x),
                abs(observer.position.y - target.position.y),
            ) <= distance

    /** Marks up to [limit] newly visible player slots in the caller-cleared [destination]. */
    fun selectAdditions(
        observer: PlayerInfoSnapshot,
        tracked: BooleanArray,
        destination: BooleanArray,
        distance: Int,
        limit: Int,
    ) {
        if (limit <= 0) return
        if (observer.mapInstance == MapInstance.SHARED) {
            selectSharedAdditions(observer, tracked, destination, distance, limit)
        } else {
            val instancePlayers = privateInstances[observer.mapInstance] ?: return
            selectPrivateAdditions(observer, instancePlayers, tracked, destination, distance, limit)
        }
    }

    private fun selectSharedAdditions(
        observer: PlayerInfoSnapshot,
        tracked: BooleanArray,
        destination: BooleanArray,
        distance: Int,
        limit: Int,
    ) {
        var selected = 0
        val zoneX = observer.position.x shr ZONE_SHIFT
        val zoneY = observer.position.y shr ZONE_SHIFT
        for ((deltaX, deltaY) in ZONE_OFFSETS) {
            val key = zoneKey(observer.position.plane, zoneX + deltaX, zoneY + deltaY)
            val players = sharedZones[key] ?: continue
            for (target in players) {
                if (
                    target.index == observer.index || tracked[target.index] ||
                    !isVisible(observer, target, distance)
                ) {
                    continue
                }
                destination[target.index] = true
                selected++
                if (selected == limit) return
            }
        }
    }

    private fun selectPrivateAdditions(
        observer: PlayerInfoSnapshot,
        instancePlayers: List<PlayerInfoSnapshot>,
        tracked: BooleanArray,
        destination: BooleanArray,
        distance: Int,
        limit: Int,
    ) {
        if (instancePlayers.size <= limit) {
            for (target in instancePlayers) {
                if (
                    target.index != observer.index && !tracked[target.index] &&
                    isVisible(observer, target, distance)
                ) {
                    destination[target.index] = true
                }
            }
            return
        }
        var selected = 0
        val zoneX = observer.position.x shr ZONE_SHIFT
        val zoneY = observer.position.y shr ZONE_SHIFT
        for ((deltaX, deltaY) in ZONE_OFFSETS) {
            val key = zoneKey(observer.position.plane, zoneX + deltaX, zoneY + deltaY)
            var index = instancePlayers.firstInZone(key)
            while (index < instancePlayers.size) {
                val target = instancePlayers[index++]
                if (zoneKey(target) != key) break
                if (
                    target.index == observer.index || tracked[target.index] ||
                    !isVisible(observer, target, distance)
                ) {
                    continue
                }
                destination[target.index] = true
                selected++
                if (selected == limit) return
            }
        }
    }

    private fun List<PlayerInfoSnapshot>.firstInZone(key: Int): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (zoneKey(this[middle]) < key) low = middle + 1 else high = middle
        }
        return low
    }

    private companion object {
        const val PLAYER_SLOTS = 2_048
        const val ZONE_SHIFT = 3
        const val ZONE_RADIUS =
            (PlayerInfoViewport.PREFERRED_DISTANCE + (1 shl ZONE_SHIFT) - 1) shr ZONE_SHIFT
        const val ZONE_KEY_SHIFT = 11
        const val PLANE_SHIFT = 22
        val PLAYER_ZONE_ORDER =
            Comparator<PlayerInfoSnapshot> { first, second ->
                val zoneOrder = zoneKey(first).compareTo(zoneKey(second))
                if (zoneOrder != 0) zoneOrder else first.index.compareTo(second.index)
            }
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

        fun zoneKey(snapshot: PlayerInfoSnapshot): Int =
            zoneKey(snapshot.position.plane, snapshot.position.x shr ZONE_SHIFT, snapshot.position.y shr ZONE_SHIFT)

        fun zoneKey(plane: Int, zoneX: Int, zoneY: Int): Int =
            (plane shl PLANE_SHIFT) or (zoneX shl ZONE_KEY_SHIFT) or zoneY
    }
}
