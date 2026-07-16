package emu.server.game.world.player

import emu.server.game.network.connection.PlayerConnection
import emu.server.game.persistence.PlayerWriteBack

/** World membership that composes gameplay, connection, and write-back lifetimes. */
internal data class ConnectedPlayer(
    val player: WorldPlayer,
    val connection: PlayerConnection,
    val writeBack: PlayerWriteBack,
)
