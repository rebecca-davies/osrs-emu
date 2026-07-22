package emu.server.game.network.output.npcinfo

import emu.game.map.Direction
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.Npc
import emu.game.npc.NpcList
import emu.game.npc.NpcMovementUpdate
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.NpcInfoAddition
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal

/** Retains one connection's bounded local NPC list across information phases. */
internal class NpcInfoState {
    private var tracked: BooleanArray? = null
    private val localIndexes = IntArray(NpcInfo.MAX_LOCAL_NPCS)
    private val localUids = LongArray(NpcInfo.MAX_LOCAL_NPCS)
    private val additionIndexes = IntArray(NpcInfo.MAX_LOCAL_NPCS)
    private var localCount = 0

    fun next(view: NpcInfoView, position: Tile, mapInstance: MapInstance): NpcInfo {
        if (localCount == 0 && view.isEmpty) return NpcInfo.EMPTY
        val locals = if (localCount == 0) null else ArrayList<NpcInfoLocal>(localCount)
        val currentTracked = tracked
        var retained = 0
        for (slot in 0 until localCount) {
            val index = localIndexes[slot]
            val npc = view[index]
            if (npc == null || npc.uid != localUids[slot] || !view.isVisible(position, mapInstance, npc)) {
                locals?.add(NpcInfoLocal.Remove)
                checkNotNull(currentTracked)[index] = false
            } else {
                locals?.add(npc.localUpdate())
                localIndexes[retained] = index
                localUids[retained] = npc.uid
                retained++
            }
        }
        localCount = retained
        val additionCount =
            view.selectAdditions(
                position,
                mapInstance,
                currentTracked,
                additionIndexes,
                NpcInfo.MAX_LOCAL_NPCS - localCount,
            )
        if (locals == null && additionCount == 0) return NpcInfo.EMPTY
        if (additionCount == 0) return NpcInfo(checkNotNull(locals), emptyList())
        val additions = ArrayList<NpcInfoAddition>(additionCount)
        val active = currentTracked ?: BooleanArray(NpcList.DEFAULT_CAPACITY).also { tracked = it }
        for (slot in 0 until additionCount) {
            val npc = checkNotNull(view[additionIndexes[slot]])
            additions += npc.additionFrom(position)
            active[npc.index] = true
            localIndexes[localCount] = npc.index
            localUids[localCount] = npc.uid
            localCount++
        }
        return NpcInfo(locals.orEmpty(), additions)
    }

    private fun Npc.additionFrom(observer: Tile): NpcInfoAddition =
        NpcInfoAddition(
            index = index,
            type = type.id,
            deltaX = position.x - observer.x,
            deltaY = position.y - observer.y,
            orientation = orientation.npcInfoIndex(),
        )

    private fun Npc.localUpdate(): NpcInfoLocal =
        when (val movement = movementUpdate) {
            NpcMovementUpdate.Idle -> NpcInfoLocal.Idle
            is NpcMovementUpdate.Walk -> NpcInfoLocal.Walk(movement.direction.npcInfoIndex())
        }

    private fun Direction.npcInfoIndex(): Int =
        when (this) {
            Direction.NORTH_WEST -> 0
            Direction.NORTH -> 1
            Direction.NORTH_EAST -> 2
            Direction.WEST -> 3
            Direction.EAST -> 4
            Direction.SOUTH_WEST -> 5
            Direction.SOUTH -> 6
            Direction.SOUTH_EAST -> 7
        }
}
