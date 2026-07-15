package emu.server.world.network

import emu.game.cycle.CycleProfileSnapshot
import emu.persistence.account.PlayerRank
import emu.protocol.osrs239.game.message.MessageGame

/** Formats world-cycle telemetry for administrator accounts. */
internal object AdminCycleOutput {
    fun message(rank: PlayerRank, snapshot: CycleProfileSnapshot): MessageGame? {
        if (rank != PlayerRank.ADMINISTRATOR) return null
        val text =
            "Cycle profile: cycles=${snapshot.cycles}, avg=${millis(snapshot.averageNanos)}ms, " +
                "max=${millis(snapshot.maxNanos)}ms, lag spikes=${snapshot.lagSpikes}."
        return MessageGame(MessageGame.GAME_MESSAGE, text)
    }

    private fun millis(nanos: Long): Double = nanos / 1_000_000.0
}
