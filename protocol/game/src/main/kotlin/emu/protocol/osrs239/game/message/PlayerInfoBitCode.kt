package emu.protocol.osrs239.game.message

/** One run or active update within a rev-239 player-info section. */
sealed interface PlayerInfoBitCode {
    data class Skip(val players: Int) : PlayerInfoBitCode {
        init {
            require(players in 1..MAX_SKIP_PLAYERS) { "player-info skip must cover 1..$MAX_SKIP_PLAYERS players" }
        }
    }

    data class HighResolution(
        val movement: PlayerMovement? = null,
        val update: PlayerInfoUpdate? = null,
    ) : PlayerInfoBitCode {
        init {
            require(movement != null || update != null) { "idle high-resolution players must use a skip" }
        }
    }

    data class Remove(val regionChange: LowResolution? = null) : PlayerInfoBitCode

    data class Add(
        val x: Int,
        val y: Int,
        val update: PlayerInfoUpdate,
        val regionChange: LowResolution? = null,
    ) : PlayerInfoBitCode {
        init {
            require(x in LOCAL_COORDINATES && y in LOCAL_COORDINATES) {
                "low-to-high player coordinates must fit 13 bits"
            }
        }
    }

    sealed interface LowResolution : PlayerInfoBitCode {
        data class Plane(val delta: Int) : LowResolution {
            init {
                require(delta in 0..3) { "plane delta must fit two bits" }
            }
        }

        data class Step(val planeDelta: Int, val direction: Int) : LowResolution {
            init {
                require(planeDelta in 0..3) { "plane delta must fit two bits" }
                require(direction in 0..7) { "low-resolution direction must fit three bits" }
            }
        }

        data class Region(val planeDelta: Int, val deltaX: Int, val deltaY: Int) : LowResolution {
            init {
                require(planeDelta in 0..3) { "plane delta must fit two bits" }
                require(deltaX in 0..255 && deltaY in 0..255) { "region deltas must fit eight bits" }
            }
        }
    }

    private companion object {
        const val MAX_SKIP_PLAYERS = 2_048
        val LOCAL_COORDINATES = 0..0x1FFF
    }
}
