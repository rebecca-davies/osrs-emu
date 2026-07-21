package emu.game.player.appearance

private val BODY_KIT_IDS = 0..1_791
private val HAIR_COLOR_INDICES = 0..29
private val TORSO_COLOR_INDICES = 0..28
private val LEG_COLOR_INDICES = 0..28
private val FEET_COLOR_INDICES = 0..5
private val SKIN_COLOR_INDICES = 0..13

/** Character gender used to select the matching identity-kit family. */
enum class CharacterGender(val id: Int) {
    MALE(0),
    FEMALE(1),
    ;

    companion object {
        fun fromId(id: Int): CharacterGender =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("invalid character gender $id")
    }
}

/**
 * Identity-kit ids for the seven configurable player body parts.
 *
 * Values cover every kit representable by a player appearance; creation and content remain
 * responsible for choosing selectable kits from the active cache catalog.
 */
data class CharacterBodyKits(
    val hair: Int,
    val jaw: Int,
    val torso: Int,
    val arms: Int,
    val hands: Int,
    val legs: Int,
    val feet: Int,
) {
    init {
        require(
            hair in BODY_KIT_IDS &&
                jaw in BODY_KIT_IDS &&
                torso in BODY_KIT_IDS &&
                arms in BODY_KIT_IDS &&
                hands in BODY_KIT_IDS &&
                legs in BODY_KIT_IDS &&
                feet in BODY_KIT_IDS,
        ) {
            "character body-kit ids must map below the worn-item appearance range"
        }
    }

    companion object {
        /** Baseline male identity kits. */
        val DEFAULT = CharacterBodyKits(hair = 0, jaw = 10, torso = 18, arms = 26, hands = 33, legs = 36, feet = 42)
    }
}

/** Palette indices in the client's hair, torso, legs, feet, and skin order. */
data class CharacterColors(
    val hair: Int,
    val torso: Int,
    val legs: Int,
    val feet: Int,
    val skin: Int,
) {
    init {
        require(
            hair in HAIR_COLOR_INDICES &&
                torso in TORSO_COLOR_INDICES &&
                legs in LEG_COLOR_INDICES &&
                feet in FEET_COLOR_INDICES &&
                skin in SKIN_COLOR_INDICES,
        ) {
            "character colour index is outside its build-239 palette"
        }
    }

    companion object {
        /** First client palette entry for each configurable colour. */
        val DEFAULT = CharacterColors(hair = 0, torso = 0, legs = 0, feet = 0, skin = 0)
    }
}

/** Protocol-independent visual state for one character. */
data class CharacterAppearance(
    val gender: CharacterGender,
    val bodyKits: CharacterBodyKits,
    val colors: CharacterColors,
) {
    companion object {
        /** Safe baseline appearance for a character without explicit customization. */
        val DEFAULT = CharacterAppearance(CharacterGender.MALE, CharacterBodyKits.DEFAULT, CharacterColors.DEFAULT)
    }
}
