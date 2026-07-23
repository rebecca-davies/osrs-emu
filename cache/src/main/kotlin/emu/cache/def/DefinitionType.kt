package emu.cache.def

/**
 * Definition groups within the CONFIGS index, where `fileId == definitionId`.
 */
enum class DefinitionType(val group: Int) {
    OBJECT(6),
    ENUM(8),
    NPC(9),
    ITEM(10),
    VARBIT(14);

    companion object {
        /** The top-level cache index that holds every config group. */
        const val CONFIGS_INDEX: Int = 2
    }
}
