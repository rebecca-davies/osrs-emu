package emu.server.game.world

/** Determines whether a participant remains registered after its current cycle. */
internal enum class WorldParticipantResult {
    KEEP,
    REMOVE,
}
