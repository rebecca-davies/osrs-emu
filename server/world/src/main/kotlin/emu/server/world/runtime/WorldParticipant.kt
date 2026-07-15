package emu.server.world.runtime

import emu.game.cycle.CycleProfileSnapshot

/** One logged-in player advanced by the authoritative world cycle. */
internal interface WorldParticipant {
    val playerId: Long

    /** Runs non-suspending participant work for [worldTick] on the world coroutine. */
    fun cycle(worldTick: Long): WorldParticipantResult

    /** Publishes the world's shared timing report to eligible participants. */
    fun reportCycleProfile(snapshot: CycleProfileSnapshot) = Unit
}
