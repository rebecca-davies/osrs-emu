package emu.cache.def

/**
 * Decoded rev-239 object definition from index 2, group 6, where `fileId == id`.
 *
 * Every property maps to exactly one opcode (or opcode family) and is nullable/empty when that
 * opcode was absent — the class captures only what the wire actually wrote, never the loader's
 * post-load defaults (`wallOrDoor`/`supportsItems` derivations), so decode→encode is byte-faithful.
 * Sentinels are already resolved: `0xFFFF` fields are stored as `-1`, and `contrast`/`contouredGround`
 * hold the loader's scaled values (`i8 * 25`, `u8 * 256`).
 */
data class ObjectDefinition(
    val id: Int,
    val name: String? = null,
    /** opcodes 1/5/6/7: model ids. */
    val objectModels: List<Int>? = null,
    /** opcodes 1/6: per-model shape types; null when models came from opcode 5/7. */
    val objectTypes: List<Int>? = null,
    val sizeX: Int? = null,
    val sizeY: Int? = null,
    /** opcode 17 (`interactType=0`) or opcode 27 (`interactType=1`); null when neither present. */
    val interactType: Int? = null,
    /** opcode 18: clears `blocksProjectile` on its own (opcode 17 implies it and is tracked separately). */
    val blocksProjectileClear: Boolean = false,
    val wallOrDoor: Int? = null,
    /** opcode 21 (value 0) or opcode 81 (`u8 * 256`); null when neither present. */
    val contouredGround: Int? = null,
    val mergeNormals: Boolean = false,
    val modelClipped: Boolean = false,
    val animationId: Int? = null,
    val decorDisplacement: Int? = null,
    val ambient: Int? = null,
    val contrast: Int? = null,
    val ops: EntityOps = EntityOps(),
    val recolorToFind: List<Int>? = null,
    val recolorToReplace: List<Int>? = null,
    val retextureToFind: List<Int>? = null,
    val retextureToReplace: List<Int>? = null,
    val category: Int? = null,
    val rotated: Boolean = false,
    val shadowClear: Boolean = false,
    val modelSizeX: Int? = null,
    val modelSizeHeight: Int? = null,
    val modelSizeY: Int? = null,
    val mapSceneId: Int? = null,
    val blockingMask: Int? = null,
    val offsetX: Int? = null,
    val offsetHeight: Int? = null,
    val offsetY: Int? = null,
    val obstructsGround: Boolean = false,
    val hollow: Boolean = false,
    val supportsItems: Int? = null,
    val varTransform: VarTransform? = null,
    val ambientSound: AmbientSound? = null,
    val ambientSoundChange: AmbientSoundChange? = null,
    val mapAreaId: Int? = null,
    val randomizeAnimStart: Boolean = false,
    val deferAnimChange: Boolean = false,
    val soundDistanceFadeCurve: Int? = null,
    val soundFade: SoundFade? = null,
    val unknown1: Boolean = false,
    val soundVisibility: Int? = null,
    val raise: Int? = null,
    val params: Map<Int, ParamValue>? = null,
) {
    /** opcode 78: a single looping ambient sound. `retain` is the rev-220 extra byte (present at rev 239). */
    data class AmbientSound(val soundId: Int, val distance: Int, val retain: Int?)

    /** opcode 79: a randomly-cycled ambient sound set. */
    data class AmbientSoundChange(
        val ticksMin: Int,
        val ticksMax: Int,
        val distance: Int,
        val retain: Int?,
        val soundIds: List<Int>,
    )

    /** opcode 93: sound fade-in/out curves and durations. */
    data class SoundFade(
        val inCurve: Int,
        val inDuration: Int,
        val outCurve: Int,
        val outDuration: Int,
    )
}
