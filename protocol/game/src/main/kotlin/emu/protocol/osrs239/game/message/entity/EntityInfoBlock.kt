package emu.protocol.osrs239.game.message.entity

/** One revision-239 hitmark entry shared by player and NPC information blocks. */
data class InfoHitmark(
    val type: Int,
    val value: Int,
    val delay: Int = 0,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(type in SMART_RANGE) { "hitmark type must fit a smart1or2" }
        require(value in SMART_RANGE) { "hitmark value must fit a smart1or2" }
        require(delay in SMART_RANGE) { "hitmark delay must fit a smart1or2" }
        require(limit in SMART_RANGE) { "hitmark limit must fit a smart1or2" }
    }

    companion object {
        const val DEFAULT_LIMIT = 4
        private val SMART_RANGE = 0..0x7FFF
    }
}

/** One revision-239 health-bar entry shared by player and NPC information blocks. */
data class InfoHeadbar(
    val type: Int,
    val startFill: Int,
    val endFill: Int = startFill,
    val startTime: Int = 0,
    val endTime: Int = 0,
) {
    init {
        require(type in SMART_RANGE) { "headbar type must fit a smart1or2" }
        require(startTime in SMART_RANGE && endTime in SMART_RANGE) {
            "headbar times must fit a smart1or2"
        }
        require(startFill in UNSIGNED_BYTE && endFill in UNSIGNED_BYTE) {
            "headbar fills must fit an unsigned byte"
        }
    }

    private companion object {
        val SMART_RANGE = 0..0x7FFE
        val UNSIGNED_BYTE = 0..0xFF
    }
}

/** One revision-239 spot animation attached to an entity slot. */
data class InfoSpotAnimation(
    val slot: Int,
    val id: Int,
    val height: Int = 0,
    val delay: Int = 0,
) {
    init {
        require(slot in 0..0xFF) { "spot-animation slot must fit an unsigned byte" }
        require(id in 0..0xFFFE) { "spot-animation id must fit below the null sentinel" }
        require(height in 0..0xFFFF && delay in 0..0xFFFF) {
            "spot-animation height and delay must fit unsigned shorts"
        }
    }
}

/** Whether every bounded spot-animation entry targets a different client slot. */
internal fun List<InfoSpotAnimation>.hasUniqueSlots(): Boolean {
    var index = 0
    while (index < size) {
        val slot = this[index].slot
        var previous = 0
        while (previous < index) {
            if (this[previous].slot == slot) return false
            previous++
        }
        index++
    }
    return true
}

/** Revision-239 hitmark definitions used for ordinary damage and blocked hits. */
object InfoHitmarkType {
    const val BLOCK_SELF = 12
    const val BLOCK_OTHER = 13
    const val DAMAGE_SELF = 16
    const val DAMAGE_OTHER = 17
}

/** Revision-239 default 30-segment health bar. */
object InfoHeadbarType {
    const val HEALTH_30 = 0
    const val HEALTH_30_SEGMENTS = 30
}
