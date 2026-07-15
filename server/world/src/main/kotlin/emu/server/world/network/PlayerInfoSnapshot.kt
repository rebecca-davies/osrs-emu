package emu.server.world.network

import emu.game.pathfinding.MovementUpdate
import emu.game.pathfinding.Tile
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerPublicChat

/** Immutable information-phase view of one active player. */
internal data class PlayerInfoSnapshot(
    val index: Int,
    val position: Tile,
    val movement: MovementUpdate,
    val runEnabled: Boolean,
    val appearance: PlayerAppearance,
    val publicChat: PlayerPublicChat? = null,
)
