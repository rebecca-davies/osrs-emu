package emu.server.game.world

import emu.server.session.GameSessionToken

/** Registers, activates and removes player sessions at world command boundaries. */
internal interface WorldSessionRegistry {
    fun register(participant: WorldParticipant, startActive: Boolean = true): WorldRegistration

    fun attach(
        token: GameSessionToken,
        participant: WorldParticipant,
        startActive: Boolean = false,
    ): WorldRegistration

    suspend fun remove(playerId: Long)

    suspend fun activate(playerId: Long)
}
