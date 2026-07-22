package emu.game.player

import emu.game.map.Tile

internal fun testPlayer(
    initialPosition: Tile,
    savedVarps: Map<Int, Int> = emptyMap(),
): Player =
    Player(
        id = 1,
        index = 1,
        displayName = "Player",
        staffModLevel = StaffModLevel.NONE,
        initialPosition = initialPosition,
        savedVarps = savedVarps,
    )
