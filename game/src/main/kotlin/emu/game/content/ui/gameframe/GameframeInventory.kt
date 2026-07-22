package emu.game.content.ui.gameframe

/** Client inventory binding published while the initial gameframe is opened. */
data class GameframeInventory(
    val componentId: Int,
    val inventoryId: Int,
    val source: GameframeInventorySource? = null,
) {
    init {
        require(componentId in 0..0xFFFF) { "component id must fit an unsigned short" }
        require(inventoryId in 0..0xFFFF) { "inventory id must fit an unsigned short" }
    }
}

/** Authoritative player container backing a configured gameframe inventory. */
enum class GameframeInventorySource(val configName: String) {
    INVENTORY("inventory"),
    WORN("worn"),
    ;

    companion object {
        fun fromConfigName(name: String?): GameframeInventorySource? =
            entries.firstOrNull { it.configName == name }
    }
}
