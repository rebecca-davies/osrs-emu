package emu.server.game.network.wire

import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord

internal val TEST_PLAYER =
    CharacterRecord(
        id = 1,
        displayName = "Test_Player",
        position = CharacterPosition(3222, 3218, 0),
        playTimeSeconds = 0,
    )
