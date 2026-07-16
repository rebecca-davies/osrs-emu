package emu.server.game.network.output.playerinfo

import emu.game.map.Tile
import emu.game.pathfinding.movement.MovementUpdate
import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance

/** Immutable information-phase view of one active player. */
internal data class PlayerInfoSnapshot(
    val index: Int,
    val position: Tile,
    val movement: MovementUpdate,
    val runEnabled: Boolean,
    val appearance: PlayerAppearance,
    val publicChat: PlayerPublicChat? = null,
)
