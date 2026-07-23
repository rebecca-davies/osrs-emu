package emu.game.player.inventory.loadout

import emu.game.obj.ObjCatalog
import emu.game.obj.ObjStack

/** Human-readable configuration for one complete worn and backpack setup. */
internal data class PlayerLoadoutConfig(
    val name: String,
    val worn: List<ObjStackConfig>,
    val inventory: List<ObjStackConfig>,
) {
    /** Resolves this configuration against one revision-pinned object catalogue. */
    fun resolve(objs: ObjCatalog): PlayerLoadout? {
        val worn = worn.map { it.resolve(objs) ?: return null }
        val inventory = inventory.map { it.resolve(objs) ?: return null }
        return PlayerLoadout.create(name, worn, inventory)
    }
}

/** Human-readable object reference, disambiguated by type id when necessary. */
internal data class ObjStackConfig(
    val name: String,
    val type: Int?,
    val count: Int,
)

private fun ObjStackConfig.resolve(objs: ObjCatalog): ObjStack? {
    val resolved = type?.let(objs::get) ?: objs.findByName(name).singleOrNull()
    if (resolved == null || !resolved.name.equals(name, ignoreCase = true)) return null
    return ObjStack(resolved, count)
}
