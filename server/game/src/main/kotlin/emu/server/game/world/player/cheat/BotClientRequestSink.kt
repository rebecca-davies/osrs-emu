package emu.server.game.world.player.cheat

/** Nonblocking host-provided boundary for bounded headless-client launch requests. */
fun interface BotClientRequestSink {
    fun add(count: Int): BotClientRequestResult
}

/** Immediate outcome of submitting a bounded headless-client request. */
sealed interface BotClientRequestResult {
    data class Accepted(val count: Int, val reservedClients: Int) : BotClientRequestResult

    data class InvalidCount(val maximum: Int) : BotClientRequestResult

    data object CapacityReached : BotClientRequestResult

    data object Busy : BotClientRequestResult

    data object Unavailable : BotClientRequestResult
}
