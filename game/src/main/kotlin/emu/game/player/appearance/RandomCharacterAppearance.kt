package emu.game.player.appearance

import kotlin.random.Random

// The pinned build-239 identity-kit archive marks these base runs selectable for bodyPartIds 0..13.
private val MALE_BODY_KITS =
    BodyKitChoices(
        hair = 0..9,
        jaw = 10..17,
        torso = 18..25,
        arms = 26..32,
        hands = 33..34,
        legs = 36..41,
        feet = 42..43,
    )
private val FEMALE_BODY_KITS =
    BodyKitChoices(
        hair = 45..55,
        jaw = 292..306,
        torso = 56..60,
        arms = 61..66,
        hands = 67..68,
        legs = 70..78,
        feet = 79..80,
    )
private val HAIR_COLORS = 0..29
private val TORSO_COLORS = 0..28
private val LEG_COLORS = 0..28
private val FEET_COLORS = 0..5
private val SKIN_COLORS = 0..13

/** Generates a valid initial appearance from selectable build-239 identity kits and palettes. */
class RandomCharacterAppearance(
    private val nextIndex: (Int) -> Int = { bound -> Random.Default.nextInt(bound) },
) {
    fun generate(): CharacterAppearance {
        val gender = select(CharacterGender.entries)
        val body = if (gender == CharacterGender.MALE) MALE_BODY_KITS else FEMALE_BODY_KITS
        return CharacterAppearance(
            gender = gender,
            bodyKits =
                CharacterBodyKits(
                    hair = select(body.hair),
                    jaw = select(body.jaw),
                    torso = select(body.torso),
                    arms = select(body.arms),
                    hands = select(body.hands),
                    legs = select(body.legs),
                    feet = select(body.feet),
                ),
            colors =
                CharacterColors(
                    hair = select(HAIR_COLORS),
                    torso = select(TORSO_COLORS),
                    legs = select(LEG_COLORS),
                    feet = select(FEET_COLORS),
                    skin = select(SKIN_COLORS),
                ),
        )
    }

    private fun select(range: IntRange): Int {
        val size = range.last - range.first + 1
        return range.first + checkedIndex(size)
    }

    private fun <T> select(values: List<T>): T = values[checkedIndex(values.size)]

    private fun checkedIndex(bound: Int): Int =
        nextIndex(bound).also { index ->
            require(index in 0 until bound) { "appearance random source returned $index for bound $bound" }
        }
}

private data class BodyKitChoices(
    val hair: IntRange,
    val jaw: IntRange,
    val torso: IntRange,
    val arms: IntRange,
    val hands: IntRange,
    val legs: IntRange,
    val feet: IntRange,
)
