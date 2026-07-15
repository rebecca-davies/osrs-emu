package emu.server.world.network

import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord

internal val TEST_PLAYER =
    PlayerRecord(
        id = 1,
        displayName = "Test_Player",
        position = PlayerPosition(3222, 3218, 0),
        playTimeSeconds = 0,
    )
