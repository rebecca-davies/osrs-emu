package emu.game.content.areas.inferno

import emu.game.obj.ObjCatalog
import emu.game.obj.ObjType
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScriptContext

/** Rev-239 beta object picker backed by authoritative cache definitions. */
object InfernoEquipmentScripts {
    fun register(content: PlayerContent, objs: ObjCatalog) {
        content.onButton(EQUIPMENT_BUTTON) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            val selected =
                objDialog(
                    title = "Choose an item:",
                    stockMarketRestriction = false,
                    showLastSearched = true,
                )
            val type = objs[selected]
            if (type == null) {
                player.messageGame("That item is not available in this beta world.")
                return@onButton
            }
            giveBetaObj(type)
        }
    }

    private fun PlayerScriptContext.giveBetaObj(type: ObjType) {
        val count = if (type.stackable) BETA_STACK_SIZE else 1
        if (type.wearpos != null) {
            player.worn.equip(type, count)
            player.messageGame("Equipped ${type.name}.")
        } else if (player.inventory.add(type, count)) {
            player.messageGame("Added ${type.name} to your inventory.")
        } else {
            player.messageGame("Your inventory is full.")
        }
    }

    private const val EQUIPMENT_BUTTON = "wornitems:equipment"
    private const val BETA_STACK_SIZE = 10_000
}
