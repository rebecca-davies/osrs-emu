package emu.server.game.network.input

/** Terminates a connection that produces actions faster than the world can accept them. */
internal object IncomingPlayerActionQueueOverflow : RuntimeException(
    "incoming player action queue is full",
    null,
    false,
    false,
)
