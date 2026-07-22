package emu.game.script.input

import emu.game.map.Tile

/** Validated client response capable of resuming one suspended player script. */
sealed interface PlayerScriptInput

/** Integer submitted by the player's active count dialog. */
data class CountDialogInput(val count: Int) : PlayerScriptInput

/** Unsigned object type submitted by the player's active object dialog. */
data class ObjDialogInput(val obj: Int) : PlayerScriptInput {
    init {
        require(obj in 0..0xFFFF) { "object dialog type must fit an unsigned short" }
    }
}

/** World tile selected by the player's next route click. */
data class TileInput(val tile: Tile) : PlayerScriptInput
