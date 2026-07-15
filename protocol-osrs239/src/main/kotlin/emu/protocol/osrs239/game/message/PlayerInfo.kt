package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * PLAYER_INFO (opcode 28) for the current single-local-player world: no other players are updated
 * or added, and the local player may be idle, walking, or running. This is the standard OSRS "GPI" bit stream
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b/§4c), reconstructed from the
 * rev-239 decompile (`dy.ae`/`dy.uq`/`dy.ax`/`dy.ab`) 2026-07-14.
 *
 * @param appearance if non-null, the local player is flagged for an extended-info **appearance**
 *   block so its avatar model draws. If null, this packet carries no appearance update; the client
 *   retains the appearance established during the initial cycle.
 */
data class PlayerInfo(
    val appearance: PlayerAppearance? = null,
    val movement: PlayerMovement? = null,
    /** Cached movement speed: 0=crawl, 1=walk, 2=run, 127=stationary. */
    val moveSpeed: Int? = null,
    /** Per-movement override when the queued step count disagrees with [moveSpeed]. */
    val temporaryMoveSpeed: Int? = null,
) : OutgoingMessage {
    init {
        require(moveSpeed == null || moveSpeed in VALID_MOVE_SPEEDS) { "invalid move speed $moveSpeed" }
        require(temporaryMoveSpeed == null || temporaryMoveSpeed in VALID_MOVE_SPEEDS) {
            "invalid temporary move speed $temporaryMoveSpeed"
        }
    }

    private companion object {
        val VALID_MOVE_SPEEDS = setOf(0, 1, 2, 127)
    }
}

/** Local-player movement delta encoded by the rev-239 high-resolution GPI bitcode. */
sealed interface PlayerMovement {
    val deltaX: Int
    val deltaY: Int

    /** One-tile movement using the client's three-bit direction table. */
    data class Walk(override val deltaX: Int, override val deltaY: Int) : PlayerMovement {
        init {
            require(deltaX in -1..1 && deltaY in -1..1 && (deltaX != 0 || deltaY != 0)) {
                "walk delta must be one adjacent tile"
            }
        }
    }

    /** Two-step net movement using the client's four-bit outer-ring delta table. */
    data class Run(override val deltaX: Int, override val deltaY: Int) : PlayerMovement {
        init {
            require(deltaX in -2..2 && deltaY in -2..2 && (kotlin.math.abs(deltaX) == 2 || kotlin.math.abs(deltaY) == 2)) {
                "run delta must lie on the outer ring of a 5x5 delta grid"
            }
        }
    }
}
