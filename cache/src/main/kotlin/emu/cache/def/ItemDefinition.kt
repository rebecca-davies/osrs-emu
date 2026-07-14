package emu.cache.def

/**
 * A decoded rev-239 item definition: index 2, group 10, `fileId == id` (recon doc §4c, `ItemLoader`).
 *
 * Every property maps to one opcode (family) and is null/empty when absent; the derived
 * weight-when-stackable default is never stored. Model fields have both a u16 and an i32 opcode
 * form ([encode][emu.cache.def.codec.ItemDefCodec] picks the u16 form unless a value exceeds
 * `0xFFFF`); `xOffset2d`/`yOffset2d` hold the loader's signed values.
 */
data class ItemDefinition(
    val id: Int,
    /** opcodes 1/44: inventory model id. */
    val inventoryModel: Int? = null,
    val name: String? = null,
    val examine: String? = null,
    val zoom2d: Int? = null,
    val xan2d: Int? = null,
    val yan2d: Int? = null,
    val xOffset2d: Int? = null,
    val yOffset2d: Int? = null,
    val unknown1: String? = null,
    /** opcode 11 (value 1) or opcode 160 (value 2); null when neither present. */
    val stackable: Int? = null,
    val cost: Int? = null,
    val wearPos1: Int? = null,
    val wearPos2: Int? = null,
    val tradeableClear: Boolean = false,
    val members: Boolean = false,
    /** opcodes 23/45: male model 0, paired with [maleOffset]. */
    val maleModel0: Int? = null,
    val maleOffset: Int? = null,
    /** opcodes 24/46. */
    val maleModel1: Int? = null,
    /** opcodes 25/48: female model 0, paired with [femaleOffset]. */
    val femaleModel0: Int? = null,
    val femaleOffset: Int? = null,
    /** opcodes 26/49. */
    val femaleModel1: Int? = null,
    val wearPos3: Int? = null,
    val groundOps: EntityOps = EntityOps(),
    /** opcodes 35-39: inventory interface options, keyed by slot 0..4. */
    val interfaceOptions: Map<Int, String> = emptyMap(),
    val colorFind: List<Int>? = null,
    val colorReplace: List<Int>? = null,
    val textureFind: List<Int>? = null,
    val textureReplace: List<Int>? = null,
    val shiftClickDropIndex: Int? = null,
    /** opcode 43: sub-ops, keyed `opId (0..4) -> (subopId (0..19) -> text)`. */
    val subops: Map<Int, Map<Int, String>>? = null,
    /** opcodes 47/78. */
    val maleModel2: Int? = null,
    /** opcodes 50/79. */
    val femaleModel2: Int? = null,
    /** opcodes 51/90. */
    val maleHeadModel: Int? = null,
    /** opcodes 52/92. */
    val maleHeadModel2: Int? = null,
    /** opcodes 53/91. */
    val femaleHeadModel: Int? = null,
    /** opcodes 54/93. */
    val femaleHeadModel2: Int? = null,
    val geTradeable: Boolean = false,
    val weight: Int? = null,
    val category: Int? = null,
    val zan2d: Int? = null,
    val notedId: Int? = null,
    val notedTemplate: Int? = null,
    /** opcodes 100-109: stack-count variants, keyed by slot 0..9. */
    val stackVariants: Map<Int, StackVariant> = emptyMap(),
    val resizeX: Int? = null,
    val resizeY: Int? = null,
    val resizeZ: Int? = null,
    val ambient: Int? = null,
    val contrast: Int? = null,
    val team: Int? = null,
    val boughtId: Int? = null,
    val boughtTemplateId: Int? = null,
    val placeholderId: Int? = null,
    val placeholderTemplateId: Int? = null,
    val params: Map<Int, ParamValue>? = null,
) {
    /** One opcode-100..109 stack variant: display model [obj] once quantity reaches [count]. */
    data class StackVariant(val obj: Int, val count: Int)
}
