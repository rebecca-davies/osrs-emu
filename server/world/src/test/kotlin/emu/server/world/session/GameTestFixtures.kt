package emu.server.world.session

import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord

internal val TEST_PLAYER =
    PlayerRecord(
        id = 1,
        username = "test player",
        displayName = "Test_Player",
        position = PlayerPosition(3222, 3218, 0),
        playTimeSeconds = 0,
    )
