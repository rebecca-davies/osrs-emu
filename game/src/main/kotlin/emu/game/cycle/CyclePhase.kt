package emu.game.cycle

/**
 * Mutation and publication phases in one RuneScape world cycle.
 *
 * Declaration order is the authoritative global phase order. Each phase completes before the next
 * phase begins so all players observe the same world state for a cycle.
 */
enum class CyclePhase {
    WORLD,
    CLIENT_INPUT,
    NPC_EVENT,
    NPC,
    PLAYER,
    LOGOUT,
    LOGIN,
    ZONE,
    INFO,
    CLIENT_OUTPUT,
    CLEANUP,
}
