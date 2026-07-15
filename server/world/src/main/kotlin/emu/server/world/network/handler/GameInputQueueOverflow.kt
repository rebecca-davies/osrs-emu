package emu.server.world.network.handler

/** Terminates a connection that produces actions faster than the world can accept them. */
internal object GameInputQueueOverflow : RuntimeException(
    "game input queue is full",
    null,
    false,
    false,
)
