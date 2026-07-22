package emu.game.script.input

import emu.game.map.Tile

/** Validated client response capable of resuming one suspended player script. */
sealed interface PlayerScriptInput

data class CountDialogInput(val count: Int) : PlayerScriptInput

data class ObjDialogInput(val obj: Int) : PlayerScriptInput {
    init {
        require(obj in 0..0xFFFF) { "object dialog type must fit an unsigned short" }
    }
}

data class TileInput(val tile: Tile) : PlayerScriptInput
