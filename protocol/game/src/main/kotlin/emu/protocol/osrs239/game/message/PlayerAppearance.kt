package emu.protocol.osrs239.game.message

/**
 * Visual and identity fields in a PLAYER_INFO appearance block.
 *
 * @param gender 0 = male, 1 = female.
 * @param headIcon skull/prayer icon; `0` means none.
 * @param equipment exactly 12 slots, order `[hat, cape, amulet, weapon, torso, shield, arms, legs,
 *   hair, hands, feet, jaw]`. Each entry is `0` for empty or `0x100 + modelId` for an identikit.
 * @param colors exactly 5 palette indices, order `[hair, torso, legs, feet/boots, skin]`.
 * @param animations exactly 7 render-animation ids, order
 *   `[stand, standTurn, walk, turnAround180, turnRight90, turnLeft90, run]`.
 * @param name CP-1252 display-name C-string.
 * @param combatLevel displayed combat level.
 * @param skillLevel secondary level displayed on skill-total worlds.
 */
data class PlayerAppearance(
    val gender: Int = GENDER_MALE,
    val headIcon: Int = 0,
    val equipment: List<Int> = DEFAULT_EQUIPMENT,
    val colors: List<Int> = DEFAULT_COLORS,
    val animations: List<Int> = DEFAULT_ANIMATIONS,
    val name: String = "player",
    val combatLevel: Int = 3,
    val skillLevel: Int = 0,
) {
    init {
        require(equipment.size == EQUIPMENT_SLOT_COUNT) {
            "equipment must have exactly $EQUIPMENT_SLOT_COUNT slots, had ${equipment.size}"
        }
        require(colors.size == COLOR_COUNT) { "colors must have exactly $COLOR_COUNT entries, had ${colors.size}" }
        require(animations.size == ANIMATION_COUNT) {
            "animations must have exactly $ANIMATION_COUNT entries, had ${animations.size}"
        }
    }

    companion object {
        const val GENDER_MALE = 0
        const val GENDER_FEMALE = 1

        const val EQUIPMENT_SLOT_COUNT = 12
        const val COLOR_COUNT = 5
        const val ANIMATION_COUNT = 7

        /** Base marker for an identikit model rather than a worn item. */
        private const val KIT_MODEL_BASE = 0x100

        /** Default male identikit; wearable slots are empty. */
        val DEFAULT_EQUIPMENT: List<Int> = listOf(
            0, // hat
            0, // cape
            0, // amulet
            0, // weapon
            KIT_MODEL_BASE + 18, // torso
            0, // shield
            KIT_MODEL_BASE + 26, // arms
            KIT_MODEL_BASE + 36, // legs
            KIT_MODEL_BASE + 0, // hair
            KIT_MODEL_BASE + 33, // hands
            KIT_MODEL_BASE + 42, // feet
            KIT_MODEL_BASE + 10, // jaw
        )

        /** First palette entry for every colour slot. */
        val DEFAULT_COLORS: List<Int> = List(COLOR_COUNT) { 0 }

        /** Default player render animations in appearance-field order. */
        val DEFAULT_ANIMATIONS: List<Int> = listOf(808, 823, 819, 820, 821, 822, 824)
    }
}
