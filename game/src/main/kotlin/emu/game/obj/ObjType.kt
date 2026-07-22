package emu.game.obj

/** Object-definition fields required by beta inventories and player appearance. */
data class ObjType(
    val id: Int,
    val name: String,
    val stackable: Boolean,
    val wearpos: Wearpos? = null,
    val wearpos2: Wearpos? = null,
    val wearpos3: Wearpos? = null,
) {
    init {
        require(id in 0..0xFFFF) { "object type must fit an unsigned short" }
        require(name.isNotBlank()) { "object name must not be blank" }
        require(wearpos != null || wearpos2 == null && wearpos3 == null) {
            "secondary wearpos fields require a primary wearpos"
        }
    }
}
