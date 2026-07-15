package emu.server.world.network

import emu.game.input.PlayerInputQueue

/** Connection-lifetime input and output capabilities used by the world thread. */
internal class GameConnection(
    val inputs: PlayerInputQueue,
    val output: GameOutputSink,
)
