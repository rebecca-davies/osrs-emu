package emu.game.player.inventory.loadout

import emu.game.obj.Obj
import emu.game.obj.ObjStack
import emu.game.player.inventory.PlayerInventory
import emu.game.player.inventory.PlayerWorn

/** Immutable, preflighted worn and backpack state that can replace a player's live containers. */
class PlayerLoadout private constructor(
    val name: String,
    internal val wornSlots: List<PlayerWorn.WornObj?>,
    internal val inventorySlots: List<Obj?>,
) {
    companion object {
        const val MAX_NAME_LENGTH = 40

        /** Builds a load-out only when every count, worn slot, and backpack stack is valid. */
        fun create(
            name: String,
            worn: List<ObjStack>,
            inventory: List<ObjStack>,
        ): PlayerLoadout? {
            if (name.isBlank() || name.length > MAX_NAME_LENGTH || '\u0000' in name) return null
            val wornCopy = worn.toList()
            val inventoryCopy = inventory.toList()
            val wornSlots = PlayerWorn().replacementWith(wornCopy) ?: return null
            val inventorySlots = PlayerInventory().replacementWith(inventoryCopy) ?: return null
            return PlayerLoadout(name, wornSlots.toList(), inventorySlots.toList())
        }
    }
}
