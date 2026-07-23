package emu.game.player.inventory.loadout

import emu.game.obj.ObjStack
import emu.game.obj.ObjType
import emu.game.player.Player

/** Atomically replaces worn and backpack state with an already preflighted [loadout]. */
fun Player.applyLoadout(loadout: PlayerLoadout) {
    inventory.replaceWith(loadout.inventorySlots)
    worn.replaceWith(loadout.wornSlots)
}

/** Adds one object quantity to the backpack without partially changing it on failure. */
fun Player.provision(type: ObjType, count: Int = 1): PlayerProvisionResult {
    if (count <= 0) return PlayerProvisionResult.INVALID
    val replacement = inventory.replacementAdding(listOf(ObjStack(type, count)))
        ?: return PlayerProvisionResult.NO_SPACE
    inventory.replaceWith(replacement)
    return PlayerProvisionResult.PROVISIONED
}

/** Equips one object and moves all displaced worn objects to the backpack atomically. */
fun Player.provisionWorn(type: ObjType, count: Int = 1): PlayerProvisionResult {
    if (count <= 0) return PlayerProvisionResult.INVALID
    val wornReplacement = worn.replacementEquipping(type, count)
        ?: return PlayerProvisionResult.INVALID
    val inventoryReplacement = inventory.replacementAdding(wornReplacement.displaced)
        ?: return PlayerProvisionResult.NO_SPACE
    inventory.replaceWith(inventoryReplacement)
    worn.replaceWith(wornReplacement.slots)
    return PlayerProvisionResult.PROVISIONED
}

/** Result of one bounded player inventory provisioning request. */
enum class PlayerProvisionResult {
    PROVISIONED,
    NO_SPACE,
    INVALID,
}
