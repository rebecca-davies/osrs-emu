package emu.server.bot

import emu.server.bot.connection.BotEndpoint

/** Host-facing lifecycle and bounded request boundary for generated headless clients. */
interface BotService {
    fun start(endpoint: BotEndpoint)

    fun add(count: Int): BotLaunchResult

    suspend fun stop()
}

/** Result of submitting one bounded headless-client launch request. */
sealed interface BotLaunchResult {
    data class Accepted(val count: Int, val reservedClients: Int) : BotLaunchResult

    data class InvalidCount(val maximum: Int) : BotLaunchResult

    data object CapacityReached : BotLaunchResult

    data object Busy : BotLaunchResult

    data object Unavailable : BotLaunchResult
}
