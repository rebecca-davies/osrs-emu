package emu.protocol.osrs239.game.message

/**
 * The visual/identity data the client needs to draw a player's avatar model — the payload of the
 * PLAYER_INFO "appearance" extended-info block
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b/§4c).
 *
 * **CONFIDENCE: MEDIUM/LOW throughout this class.** The recon confirms *that* an appearance block
 * exists and roughly where it sits (§4b, `dy.ax`), but `dy.ax`/`dy.uq`/`dy.yq` are CFR-mangled
 * (nested `blockNN:` label soup), so the exact field set, field order, and sentinel values below
 * are **not** read off the rev-239 decompile. They follow the classic OSRS-family appearance-block
 * shape that is stable and extremely well documented across the whole RS2 lineage (317 through
 * OSRS-era private-server implementations), which is the best-supported interpretation available
 * without driving the real client. A later end-to-end task must correct any field that turns out
 * wrong, using the client's `dy.ae` bytes-consumed assertion (`dy.java:120`) as the falsifier.
 *
 * All default values here are placeholders for a "nude" default human male: equipment/body-part
 * model ids are **not** real cache model ids (this emulator has no cache-derived identikit ids
 * yet) — they are small arbitrary placeholders chosen only so the encoder has deterministic,
 * testable bytes. Colors are all `0` (first palette entry) and animations are all `-1`, the
 * standard RS2-family sentinel meaning "no override, client uses its built-in default anim" —
 * this sentinel choice is HIGH confidence as a *technique* (it is how servers historically avoid
 * needing real animation ids at all), but not confirmed for rev 239 specifically.
 *
 * @param gender 0 = male, 1 = female. **HIGH** confidence on the encoding (stable across all RS2
 *   revisions); not confirmed this exact field is first in rev 239.
 * @param headIcon skull/prayer icon, `0` = none by this implementation's convention. **LOW**
 *   confidence on whether rev 239 uses `0` or `-1` as the "none" sentinel here.
 * @param equipment exactly 12 slots, order `[hat, cape, amulet, weapon, torso, shield, arms, legs,
 *   hair, hands, feet, jaw]` (the classic RS2-family slot order). Each entry is either `0`
 *   (nothing in this slot) or `0x100 + modelId` (a placeholder body-part/kit model, since we have
 *   no cache-sourced identikit ids yet). **MEDIUM** confidence on slot order/count (matches the
 *   well-known layout the task recon points at); **LOW** confidence on the placeholder model id
 *   values themselves (they are not real OSRS model ids).
 * @param colors exactly 5 palette indices, order `[hair, torso, legs, feet/boots, skin]`.
 *   **MEDIUM** confidence on the 5-slot/order convention; the recon text only calls out
 *   hair/torso/legs explicitly.
 * @param animations exactly 7 render-animation ids, order
 *   `[stand, standTurn, walk, turnAround180, turnRight90, turnLeft90, run]`. **LOW** confidence on
 *   field order; defaulted to `-1` (see class doc) so the exact ids don't matter for a first
 *   render.
 * @param name display name, encoded as the rev-239 CP-1252 C-string read by the client's
 *   appearance decoder (see [PlayerInfoEncoder]).
 * @param combatLevel **HIGH** confidence on presence/position relative to name (universal RS2
 *   convention); not confirmed for rev 239 specifically.
 * @param skillLevel a secondary level field sent after combat level (historically used for
 *   skill-only minigame worlds); **LOW** confidence this field exists at all in rev 239 — kept
 *   because the task explicitly asks for a "skill level" field, defaulted to `0` (inert).
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

        /** `0x100 + modelId` marker for a "body part / kit" model, as opposed to a worn item. */
        private const val KIT_MODEL_BASE = 0x100

        /**
         * The classic RS2-family **default male identikit** for the six body slots
         * `[torso, arms, legs, hair, hands, feet, jaw]` (indices 4, 6, 7, 8, 9, 10, 11), each
         * `0x100 + kitId` so the client reads it as an identikit model rather than a worn item;
         * every wearable slot (hat, cape, amulet, weapon, shield) defaults to `0` = empty. The kit
         * ids (torso 18, arms 26, legs 36, hair 0, hands 33, feet 42, jaw 10) are the long-standing
         * default-avatar values across the RS2 lineage — real, load-bearing model ids (so the
         * client builds a valid avatar model rather than faulting on a missing kit), used here as
         * the milestone-5 placeholder character until real per-account appearance data exists.
         */
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

        /** All palette index `0` — an inert but valid default colour set (first palette entry). */
        val DEFAULT_COLORS: List<Int> = List(COLOR_COUNT) { 0 }

        /**
         * The classic RS2-family default player render animations, order
         * `[stand, standTurn, walk, turnAround180, turnRight90, turnLeft90, run]` = 808, 823, 819,
         * 820, 821, 822, 824 — real animation ids (so the avatar has a valid idle/walk pose rather
         * than faulting on a missing sequence), the milestone-5 placeholder set.
         */
        val DEFAULT_ANIMATIONS: List<Int> = listOf(808, 823, 819, 820, 821, 822, 824)
    }
}
