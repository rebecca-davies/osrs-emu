package emu.protocol.osrs239.game.message.playerinfo

/**
 * Visual and identity fields in a PLAYER_INFO appearance block.
 *
 * @param gender 0 = male, 1 = female.
 * @param skullIcon skull icon, or `-1` for none.
 * @param prayerIcon overhead prayer icon, or `-1` for none.
 * @param body equipment, recolours, and render animations in client wire order.
 * @param name CP-1252 display-name C-string.
 * @param combatLevel displayed combat level.
 * @param skillLevel secondary level displayed on skill-total worlds.
 */
data class PlayerAppearance(
    val gender: Int = GENDER_MALE,
    val skullIcon: Int = ICON_NONE,
    val prayerIcon: Int = ICON_NONE,
    val body: PlayerBody = PlayerBody(),
    val name: String = "player",
    val combatLevel: Int = 3,
    val skillLevel: Int = 0
) {
    init {
        require(gender == GENDER_MALE || gender == GENDER_FEMALE) { "invalid player gender" }
        require(skullIcon in ICON_RANGE && prayerIcon in ICON_RANGE) {
            "player head icons must fit a signed client byte"
        }
        require(body.equipment.size == EQUIPMENT_SLOT_COUNT) {
            "equipment must have exactly $EQUIPMENT_SLOT_COUNT slots, had ${body.equipment.size}"
        }
        require(body.colors.size == COLOR_COUNT) {
            "colors must have exactly $COLOR_COUNT entries, had ${body.colors.size}"
        }
        require(body.animations.size == ANIMATION_COUNT) {
            "animations must have exactly $ANIMATION_COUNT entries, had ${body.animations.size}"
        }
        require(body.equipment.all { it in EQUIPMENT_RANGE }) { "equipment values must fit an unsigned short" }
        require(body.equipment.first() != NPC_TRANSFORM_MARKER) {
            "NPC transforms require a separate appearance model"
        }
        require(body.colors.all { it in UNSIGNED_BYTE }) { "appearance colours must fit an unsigned byte" }
        require(body.animations.all { it in ANIMATION_RANGE }) { "animation ids must fit an unsigned short" }
        require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH && '\u0000' !in name) {
            "display name must contain 1..$MAX_NAME_LENGTH non-NUL characters"
        }
        require(CP1252.newEncoder().canEncode(name)) { "display name must be encodable as CP-1252" }
        require(combatLevel in UNSIGNED_BYTE) { "combat level must fit an unsigned byte" }
        require(skillLevel in UNSIGNED_SHORT) { "skill level must fit an unsigned short" }
    }

    companion object {
        const val GENDER_MALE = 0
        const val GENDER_FEMALE = 1
        const val ICON_NONE = -1

        const val EQUIPMENT_SLOT_COUNT = 12
        const val COLOR_COUNT = 5
        const val ANIMATION_COUNT = 7
        private const val MAX_NAME_LENGTH = 12
        private const val NPC_TRANSFORM_MARKER = 0xFFFF
        private val ICON_RANGE = -1..127
        private val EQUIPMENT_RANGE = 0..0xFFFF
        private val ANIMATION_RANGE = -1..0xFFFF
        private val UNSIGNED_BYTE = 0..0xFF
        private val UNSIGNED_SHORT = 0..0xFFFF
        private val CP1252 = charset("windows-1252")

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
