package emu.server.game.world.entry

import emu.server.game.network.output.GameOutputBatch

/** World-thread-built initial state paired with its reserved player index. */
internal data class WorldLogin(
    val playerIndex: Int,
    val initialOutput: GameOutputBatch,
)
