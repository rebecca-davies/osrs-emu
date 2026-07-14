package emu.cache.def

/**
 * A decoded rev-239 npc definition: index 2, group 9, `fileId == id` (recon doc §4b, `NpcLoader`).
 *
 * As with [ObjectDefinition], every property maps to one opcode (family) and is null/empty when
 * absent; derived post-load defaults (`footprintSize`) are never stored. rev-239 gates: head icons
 * (opcode 102) use the `rev210` bitfield form, and opcode 111 means `renderPriority = 2`.
 */
data class NpcDefinition(
    val id: Int,
    val name: String? = null,
    val size: Int? = null,
    /** opcodes 1/61: body model ids. */
    val models: List<Int>? = null,
    /** opcodes 60/62: chathead model ids. */
    val chatheadModels: List<Int>? = null,
    val standingAnimation: Int? = null,
    /** opcode 14: walking animation set on its own (opcode 17's block is [walkSequence]). */
    val walkingAnimation: Int? = null,
    val idleRotateLeftAnimation: Int? = null,
    val idleRotateRightAnimation: Int? = null,
    /** opcode 17: walking + 180/left/right rotation animations. */
    val walkSequence: RotationSequence? = null,
    val category: Int? = null,
    val ops: EntityOps = EntityOps(),
    val recolorToFind: List<Int>? = null,
    val recolorToReplace: List<Int>? = null,
    val retextureToFind: List<Int>? = null,
    val retextureToReplace: List<Int>? = null,
    /** opcodes 74-79: combat stat block, keyed by slot 0..5; absent slots are omitted. */
    val stats: Map<Int, Int> = emptyMap(),
    val minimapVisibleClear: Boolean = false,
    val combatLevel: Int? = null,
    val widthScale: Int? = null,
    val heightScale: Int? = null,
    /** opcode 99 (value 1) or opcode 111 (value 2); null when neither present. */
    val renderPriority: Int? = null,
    val ambient: Int? = null,
    val contrast: Int? = null,
    /** opcode 102: head icons (rev-210 bitfield form). Unset slots are `HeadIcon(-1, -1)`. */
    val headIcons: List<HeadIcon>? = null,
    val rotationSpeed: Int? = null,
    val varTransform: VarTransform? = null,
    val interactableClear: Boolean = false,
    val rotationFlagClear: Boolean = false,
    val runAnimation: Int? = null,
    val runSequence: RotationSequence? = null,
    val crawlAnimation: Int? = null,
    val crawlSequence: RotationSequence? = null,
    val isFollower: Boolean = false,
    val lowPriorityFollowerOps: Boolean = false,
    val height: Int? = null,
    val footprintSize: Int? = null,
    val unknown1: Boolean = false,
    val idleAnimRestart: Boolean = false,
    val canHideForOverlap: Boolean = false,
    val overlapTintHSL: Int? = null,
    val zbufClear: Boolean = false,
    val params: Map<Int, ParamValue>? = null,
) {
    /** opcodes 17/115/117: a four-way (base + 180/left/right rotation) animation set. */
    data class RotationSequence(
        val animation: Int,
        val rotate180: Int,
        val rotateLeft: Int,
        val rotateRight: Int,
    )

    /** One opcode-102 head-icon slot: `(-1, -1)` marks an unset slot inside the bitfield run. */
    data class HeadIcon(val archiveId: Int, val spriteIndex: Int)
}
