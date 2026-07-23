package emu.game.content.beta.supplies

import emu.game.content.ui.config.UiContent
import emu.game.obj.NamedObjEnumCatalog
import emu.game.obj.ObjCatalog
import emu.game.player.inventory.loadout.PlayerLoadout

/** Loads the beta-world Tournament Supplies policy and binds externally contributed presets. */
internal object TournamentSuppliesCatalog {
    private val config: TournamentSuppliesConfig by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream(RESOURCE)) {
            "bundled Tournament Supplies policy is missing: $RESOURCE"
        }
        stream.bufferedReader().use { TournamentSuppliesConfigParser.parse(it.readText()) }
    }

    fun load(
        ui: UiContent,
        objs: ObjCatalog,
        enums: NamedObjEnumCatalog,
        loadouts: List<PlayerLoadout>,
    ): TournamentSupplies = TournamentSupplies(config, ui, objs, enums, loadouts)

    private const val RESOURCE = "/emu/game/content/beta/supplies/tournament_supplies.toml"
}
