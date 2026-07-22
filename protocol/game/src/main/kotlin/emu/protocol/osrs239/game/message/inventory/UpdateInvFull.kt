package emu.protocol.osrs239.game.message.inventory

import emu.transport.message.OutgoingMessage

/** Replaces every slot in one client inventory. */
data class UpdateInvFull(
    val interfaceId: Int,
    val componentId: Int,
    val inventoryId: Int,
    val objects: List<Obj> = emptyList(),
) : OutgoingMessage {
    /** One inventory slot's object id and quantity; id `-1` represents an empty slot. */
    data class Obj(val id: Int, val count: Int)
}
