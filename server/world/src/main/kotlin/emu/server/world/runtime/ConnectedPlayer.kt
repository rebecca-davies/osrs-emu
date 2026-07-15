package emu.server.world.runtime

import emu.server.world.entity.WorldPlayer
import emu.server.world.network.PlayerConnection

/** World membership that composes gameplay, connection, and write-back lifetimes. */
internal data class ConnectedPlayer(
    val player: WorldPlayer,
    val connection: PlayerConnection,
    val writeBack: PlayerWriteBack,
)
