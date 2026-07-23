package emu.game.content.beta.supplies

import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.config.UiContent
import emu.game.obj.NamedObjEnumCatalog
import emu.game.obj.Obj
import emu.game.obj.ObjCatalog
import emu.game.obj.ObjType
import emu.game.obj.Wearpos
import emu.game.player.Player
import emu.game.player.inventory.PlayerInventory
import emu.game.player.inventory.loadout.PlayerLoadout
import emu.game.player.inventory.loadout.PlayerProvisionResult
import emu.game.player.inventory.loadout.applyLoadout
import emu.game.player.inventory.loadout.provision
import emu.game.player.inventory.loadout.provisionWorn
import emu.game.ui.ClientScript
import emu.game.ui.Component
import java.util.Locale

/** Owns the beta world's authoritative item catalogue, provisioning, and loadout interface. */
class TournamentSupplies internal constructor(
    private val config: TournamentSuppliesConfig,
    ui: UiContent,
    objs: ObjCatalog,
    enums: NamedObjEnumCatalog,
    loadouts: List<PlayerLoadout>,
) {
    private val interfaceIds = TournamentSuppliesUi.resolve(ui)
    private val catalogue = resolveCatalogue(objs, enums)
    private val loadouts: List<PlayerLoadout>
    private val loadoutNames: String

    init {
        require(loadouts.size <= MAX_LOADOUTS) {
            "Tournament Supplies supports at most $MAX_LOADOUTS loadouts"
        }
        this.loadouts = loadouts.toList()
        this.loadouts.forEach(::requireCatalogueObjects)
        require(this.loadouts.none { SEPARATOR in it.name }) {
            "Tournament Supplies loadout names cannot contain '$SEPARATOR'"
        }
        require(this.loadouts.all { CP_1252.newEncoder().canEncode(it.name) }) {
            "Tournament Supplies loadout names must be encodable as CP-1252"
        }
        require(this.loadouts.map { it.name.lowercase(Locale.ROOT) }.distinct().size == this.loadouts.size) {
            "Tournament Supplies loadout names must be unique"
        }
        loadoutNames = this.loadouts.joinToString(SEPARATOR.toString(), transform = PlayerLoadout::name)
        require(loadoutNames.length <= MAX_LOADOUT_CATALOGUE_LENGTH) {
            "Tournament Supplies loadout catalogue exceeds $MAX_LOADOUT_CATALOGUE_LENGTH characters"
        }
    }

    /** Opens the stock revision-239 modal directly on its loadout pane. */
    fun open(player: Player) {
        val selected = selectedIndex(player)
        player.interfaces.runClientScript(interfaceIds.openMainModal, DEFAULT_COLOUR, DEFAULT_TRANSPARENCY)
        player.interfaces.openModal(interfaceIds.modalDestination, interfaceIds.root.interfaceId)
        player.varps[PlayerVarpCatalog.TOURNAMENT_LOADOUT] = selected?.plus(1) ?: 0
        player.interfaces.runClientScript(interfaceIds.populateLoadouts, loadoutNames)
        player.interfaces.runClientScript(
            interfaceIds.switchLayer,
            interfaceIds.loadoutContainer.packed,
            interfaceIds.switchLayerComponent.packed,
            interfaceIds.itemContainer.packed,
            ITEMS_LABEL,
            interfaceIds.loadoutContainer.packed,
            LOADOUTS_LABEL,
            interfaceIds.itemList.packed,
            interfaceIds.itemScrollbar.packed,
            interfaceIds.itemSearch.packed,
            ITEM_HORIZONTAL_SPACING,
            ITEM_VERTICAL_SPACING,
        )
        publishPreview(player, selected?.let(loadouts::get))
    }

    /** Selects and publishes one zero-based contributed loadout row. */
    fun select(player: Player, index: Int) {
        if (index !in loadouts.indices) return
        player.varps[PlayerVarpCatalog.TOURNAMENT_LOADOUT] = index + 1
        publishPreview(player, loadouts[index])
    }

    /** Replaces worn and backpack state with the selected, fully preflighted loadout. */
    fun apply(player: Player) {
        val selected = selectedIndex(player)?.let(loadouts::get)
        if (selected == null) {
            player.messageGame("No loadout is available.")
            return
        }
        player.applyLoadout(selected)
        player.messageGame("Applied ${selected.name}.")
    }

    /** Handles a server-authorized item-catalogue operation without trusting its transmitted name. */
    fun take(player: Player, typeId: Int, requestedCount: Int? = null) {
        val type = catalogue[typeId]
        if (type == null) {
            player.messageGame("That item is not available in this beta world.")
            return
        }
        val count = requestedCount ?: if (type.stackable) config.itemStackSize else 1
        if (count <= 0) return
        if (requestedCount == null && type.wearpos != null) {
            equip(player, type, count)
            return
        }
        when (player.provision(type, count)) {
            PlayerProvisionResult.PROVISIONED ->
                player.messageGame("Added ${displayCount(count)}${type.name} to your inventory.")
            PlayerProvisionResult.NO_SPACE ->
                player.messageGame("Your inventory does not have enough space.")
            PlayerProvisionResult.INVALID -> Unit
        }
    }

    /** Sends a short server-side examine fallback for an authorized catalogue object. */
    fun examine(player: Player, typeId: Int) {
        catalogue[typeId]?.let { player.messageGame(it.name) }
    }

    private fun equip(player: Player, type: ObjType, count: Int) {
        when (player.provisionWorn(type, count)) {
            PlayerProvisionResult.PROVISIONED -> player.messageGame("Equipped ${type.name}.")
            PlayerProvisionResult.NO_SPACE ->
                player.messageGame("Your inventory does not have enough space for your current equipment.")
            PlayerProvisionResult.INVALID ->
                player.messageGame("That item cannot be equipped.")
        }
    }

    private fun publishPreview(player: Player, loadout: PlayerLoadout?) {
        val preview = MutableList<Obj?>(Wearpos.entries.size + PlayerInventory.CAPACITY) { null }
        loadout?.wornSlots?.forEachIndexed { slot, worn ->
            preview[slot] = worn?.obj
        }
        loadout?.inventorySlots?.forEachIndexed { slot, obj ->
            preview[Wearpos.entries.size + slot] = obj
        }
        player.interfaces.transmitInventory(config.previewInventory, preview)
    }

    private fun selectedIndex(player: Player): Int? {
        if (loadouts.isEmpty()) return null
        val selected = player.varps[PlayerVarpCatalog.TOURNAMENT_LOADOUT] - 1
        return selected.takeIf { it in loadouts.indices } ?: 0
    }

    private fun resolveCatalogue(objs: ObjCatalog, enums: NamedObjEnumCatalog): Map<Int, ObjType> {
        val types = requireNotNull(enums[config.itemCatalogueEnum]) {
            "Tournament Supplies item enum ${config.itemCatalogueEnum} is unavailable"
        }
        require(types.size <= MAX_ITEM_CATALOGUE_SIZE) {
            "Tournament Supplies item catalogue exceeds $MAX_ITEM_CATALOGUE_SIZE entries"
        }
        return buildMap {
            for (id in types) {
                require(id in 0..0xFFFF) { "Tournament Supplies item type must fit an unsigned short" }
                val type = requireNotNull(objs[id]) {
                    "Tournament Supplies item type $id is unavailable in this cache"
                }
                putIfAbsent(id, type)
            }
        }
    }

    private fun requireCatalogueObjects(loadout: PlayerLoadout) {
        val unavailable =
            loadout.wornSlots.firstNotNullOfOrNull { it?.obj?.type?.takeUnless(catalogue::containsKey) }
                ?: loadout.inventorySlots.firstNotNullOfOrNull { it?.type?.takeUnless(catalogue::containsKey) }
        require(unavailable == null) {
            "Tournament Supplies loadout '${loadout.name}' uses unavailable object type $unavailable"
        }
    }

    private fun displayCount(count: Int): String = if (count == 1) "" else "$count x "

    private data class TournamentSuppliesUi(
        val modalDestination: Component,
        val root: Component,
        val switchLayerComponent: Component,
        val itemContainer: Component,
        val itemList: Component,
        val itemScrollbar: Component,
        val itemSearch: Component,
        val loadoutContainer: Component,
        val populateLoadouts: ClientScript,
        val switchLayer: ClientScript,
        val openMainModal: ClientScript,
    ) {
        companion object {
            fun resolve(ui: UiContent): TournamentSuppliesUi =
                TournamentSuppliesUi(
                    modalDestination = ui.components.require("toplevel_osrs_stretch:mainmodal"),
                    root = ui.components.require("tournament_supplies:universe"),
                    switchLayerComponent = ui.components.require("tournament_supplies:switchlayer"),
                    itemContainer = ui.components.require("tournament_supplies:item_container"),
                    itemList = ui.components.require("tournament_supplies:list"),
                    itemScrollbar = ui.components.require("tournament_supplies:scrollbar"),
                    itemSearch = ui.components.require("tournament_supplies:search"),
                    loadoutContainer = ui.components.require("tournament_supplies:loadout_container"),
                    populateLoadouts = ui.clientScripts.require("tournament_supplies:populate_loadouts"),
                    switchLayer = ui.clientScripts.require("tournament_supplies:switchlayer"),
                    openMainModal = ui.clientScripts.require("toplevel:mainmodal_open"),
                )
        }
    }

    private companion object {
        const val ITEMS_LABEL = "Items"
        const val LOADOUTS_LABEL = "Load-outs"
        const val ITEM_HORIZONTAL_SPACING = 9
        const val ITEM_VERTICAL_SPACING = 42
        const val DEFAULT_COLOUR = -1
        const val DEFAULT_TRANSPARENCY = -1
        const val MAX_ITEM_CATALOGUE_SIZE = 1_024
        const val MAX_LOADOUTS = 32
        const val MAX_LOADOUT_CATALOGUE_LENGTH = 2_048
        const val SEPARATOR = '|'
        val CP_1252 = charset("windows-1252")
    }
}
