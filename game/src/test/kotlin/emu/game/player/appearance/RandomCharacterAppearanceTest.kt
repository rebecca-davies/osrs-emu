package emu.game.player.appearance

import kotlin.test.Test
import kotlin.test.assertEquals

class RandomCharacterAppearanceTest {
    @Test
    fun `first catalog selections produce the baseline male appearance`() {
        val bounds = mutableListOf<Int>()
        val appearance =
            RandomCharacterAppearance { bound ->
                bounds += bound
                0
            }.generate()

        assertEquals(CharacterAppearance.DEFAULT, appearance)
        assertEquals(listOf(2, 10, 8, 8, 7, 2, 6, 2, 30, 29, 29, 6, 14), bounds)
    }

    @Test
    fun `last catalog selections remain valid female kits and palette indices`() {
        val appearance = RandomCharacterAppearance { bound -> bound - 1 }.generate()

        assertEquals(
            CharacterAppearance(
                gender = CharacterGender.FEMALE,
                bodyKits =
                    CharacterBodyKits(
                        hair = 55,
                        jaw = 306,
                        torso = 60,
                        arms = 66,
                        hands = 68,
                        legs = 78,
                        feet = 80,
                    ),
                colors = CharacterColors(hair = 29, torso = 28, legs = 28, feet = 5, skin = 13),
            ),
            appearance,
        )
    }
}
