package emu.protocol.osrs239.game.message.playerinfo

import kotlin.math.abs

/** High-resolution movement delta encoded by the rev-239 player-info bitcode. */
sealed interface PlayerMovement {
    val deltaX: Int
    val deltaY: Int

    data class Walk(override val deltaX: Int, override val deltaY: Int) : PlayerMovement {
        init {
            require(deltaX in -1..1 && deltaY in -1..1 && (deltaX != 0 || deltaY != 0)) {
                "walk delta must be one adjacent tile"
            }
        }
    }

    data class Run(override val deltaX: Int, override val deltaY: Int) : PlayerMovement {
        init {
            require(deltaX in -2..2 && deltaY in -2..2 && (abs(deltaX) == 2 || abs(deltaY) == 2)) {
                "run delta must lie on the outer ring of a 5x5 delta grid"
            }
        }
    }

    data class Teleport(
        override val deltaX: Int,
        override val deltaY: Int,
        val planeDelta: Int,
    ) : PlayerMovement {
        init {
            require(deltaX in WORLD_DELTA && deltaY in WORLD_DELTA) {
                "teleport delta must fit the 14-bit world coordinate"
            }
            require(planeDelta in 0..3) { "teleport plane delta must be in 0..3" }
        }
    }

    private companion object {
        val WORLD_DELTA = -0x3FFF..0x3FFF
    }
}
