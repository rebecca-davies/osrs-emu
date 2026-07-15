package emu.server.world.runtime

/** Determines whether a participant remains registered after its current cycle. */
internal enum class WorldParticipantResult {
    KEEP,
    REMOVE,
}
