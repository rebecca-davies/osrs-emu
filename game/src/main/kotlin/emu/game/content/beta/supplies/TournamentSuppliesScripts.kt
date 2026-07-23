package emu.game.content.beta.supplies

import emu.game.script.content.PlayerContent

/** Interface bindings for the beta world's stock Tournament Supplies modal. */
internal object TournamentSuppliesScripts {
    fun register(content: PlayerContent, supplies: TournamentSupplies) {
        content.onButton(EQUIPMENT_BUTTON) {
            if (lastButton?.isPrimaryComponentClick == true) supplies.open(player)
        }
        content.onButton(ITEM_LIST) {
            val click = lastButton ?: return@onButton
            if (click.sub < 0 || click.obj < 0) return@onButton
            when (click.op) {
                TAKE -> supplies.take(player, click.obj)
                TAKE_FIVE -> supplies.take(player, click.obj, 5)
                TAKE_TEN -> supplies.take(player, click.obj, 10)
                TAKE_X -> supplies.take(player, click.obj, numberDialog("How many would you like?"))
                EXAMINE -> supplies.examine(player, click.obj)
            }
        }
        content.onButton(LOADOUT_LIST) {
            val click = lastButton ?: return@onButton
            if (click.op == PRIMARY && click.sub >= 0 && click.obj == -1) {
                supplies.select(player, click.sub)
            }
        }
        content.onButton(APPLY_BUTTON) {
            if (lastButton?.isPrimaryComponentClick == true) supplies.apply(player)
        }
    }

    private const val EQUIPMENT_BUTTON = "wornitems:equipment"
    private const val ITEM_LIST = "tournament_supplies:list"
    private const val LOADOUT_LIST = "tournament_supplies:catalogue_list"
    private const val APPLY_BUTTON = "tournament_supplies:loadout_apply"
    private const val PRIMARY = 1
    private const val TAKE = 1
    private const val TAKE_FIVE = 2
    private const val TAKE_TEN = 3
    private const val TAKE_X = 4
    private const val EXAMINE = 10
}
