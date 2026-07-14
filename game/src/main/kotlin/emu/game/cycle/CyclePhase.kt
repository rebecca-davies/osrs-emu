package emu.game.cycle

/**
 * Mutation and publication phases in one RuneScape world cycle.
 *
 * Declaration order is authoritative. It follows LostCity's `World.cycle`: world work, inbound
 * client intents, NPC events, NPCs, players, logout, login, zones, update-info construction,
 * client output, then cleanup.
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
