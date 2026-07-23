package emu.game.content.areas.inferno

import emu.game.obj.ObjCatalog
import emu.game.player.inventory.loadout.PlayerLoadout
import emu.game.player.inventory.loadout.PlayerLoadoutConfigParser

/** Loads the dedicated Inferno presets contributed to the beta-world loadout interface. */
internal object InfernoLoadoutCatalog {
    fun load(objs: ObjCatalog): List<PlayerLoadout> {
        val stream = checkNotNull(javaClass.getResourceAsStream(RESOURCE)) {
            "bundled Inferno loadouts are missing: $RESOURCE"
        }
        val configs = stream.bufferedReader().use { PlayerLoadoutConfigParser.parse(it.readText()) }
        require(configs.isNotEmpty()) { "bundled Inferno loadouts must contain at least one preset" }
        return configs.map { config ->
            requireNotNull(config.resolve(objs)) {
                "Inferno loadout '${config.name}' is invalid for this cache"
            }
        }
    }

    private const val RESOURCE = "/emu/game/content/areas/inferno/loadouts.toml"
}
