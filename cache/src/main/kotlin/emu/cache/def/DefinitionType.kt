package emu.cache.def

/**
 * The three content definition types this toolchain round-trips, each addressed as a group within
 * the CONFIGS index (index 2), with `fileId == definitionId` (recon doc §5, `ConfigType`).
 */
enum class DefinitionType(val group: Int) {
    OBJECT(6),
    NPC(9),
    ITEM(10);

    companion object {
        /** The top-level cache index that holds every config group. */
        const val CONFIGS_INDEX: Int = 2
    }
}
