package emu.server.world.runtime

import emu.server.world.network.GameOutputBatch

/** World-thread-built initial state paired with its reserved player index. */
internal data class WorldLogin(
    val playerIndex: Int,
    val initialOutput: GameOutputBatch,
)
