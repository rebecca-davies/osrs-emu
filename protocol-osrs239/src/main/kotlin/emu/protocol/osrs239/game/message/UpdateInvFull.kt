package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Replaces an inventory; the initial cycle currently uses empty social/account containers. */
data class UpdateInvFull(
    val interfaceId: Int,
    val componentId: Int,
    val inventoryId: Int,
    val objects: List<Obj> = emptyList(),
) : OutgoingMessage {
    /** One inventory slot's object id and quantity. */
    data class Obj(val id: Int, val count: Int)
}
