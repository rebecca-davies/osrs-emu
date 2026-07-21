package emu.game.player.appearance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CharacterAppearanceTest {
    @Test
    fun `appearance enforces build-239 palette and identity-kit boundaries`() {
        val colors = CharacterColors(hair = 29, torso = 28, legs = 28, feet = 5, skin = 13)
        val body =
            CharacterBodyKits(
                hair = 1_791,
                jaw = 1_791,
                torso = 1_791,
                arms = 1_791,
                hands = 1_791,
                legs = 1_791,
                feet = 1_791,
            )

        assertEquals(29, colors.hair)
        assertEquals(1_791, body.hair)
        assertFailsWith<IllegalArgumentException> {
            CharacterColors(hair = 30, torso = 0, legs = 0, feet = 0, skin = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterBodyKits(hair = -1, jaw = 10, torso = 18, arms = 26, hands = 33, legs = 36, feet = 42)
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterBodyKits(hair = 1_792, jaw = 10, torso = 18, arms = 26, hands = 33, legs = 36, feet = 42)
        }
        assertFailsWith<IllegalArgumentException> { CharacterGender.fromId(2) }
    }
}
