package emu.persistence.postgres.character.storage

import emu.game.player.appearance.CharacterAppearance
import java.sql.PreparedStatement

/** Binds the normalized appearance columns beginning at [firstParameter]. */
internal fun PreparedStatement.bindCharacterAppearance(
    firstParameter: Int,
    appearance: CharacterAppearance,
) {
    val body = appearance.bodyKits
    val colors = appearance.colors
    setInt(firstParameter, appearance.gender.id)
    setInt(firstParameter + 1, body.hair)
    setInt(firstParameter + 2, body.jaw)
    setInt(firstParameter + 3, body.torso)
    setInt(firstParameter + 4, body.arms)
    setInt(firstParameter + 5, body.hands)
    setInt(firstParameter + 6, body.legs)
    setInt(firstParameter + 7, body.feet)
    setInt(firstParameter + 8, colors.hair)
    setInt(firstParameter + 9, colors.torso)
    setInt(firstParameter + 10, colors.legs)
    setInt(firstParameter + 11, colors.feet)
    setInt(firstParameter + 12, colors.skin)
}
