package emu.game.entity

/** Immutable per-cycle combat visuals published by player and NPC information updates. */
data class EntityInfoSnapshot(
    val hitmarks: List<EntityHitmark> = emptyList(),
    val healthBar: EntityHealthBar? = null,
    val spotAnimations: List<EntitySpotAnimation> = emptyList(),
) {
    val isEmpty: Boolean
        get() = hitmarks.isEmpty() && healthBar == null && spotAnimations.isEmpty()
}

/** One damage value shown after [delay] client cycles. */
data class EntityHitmark(val damage: Int, val delay: Int = 0) {
    init {
        require(damage in 0..0x7FFF) { "hitmark damage must fit a smart1or2" }
        require(delay in 0..0x7FFF) { "hitmark delay must fit a smart1or2" }
    }
}

/** One health-bar value expressed as authoritative current and maximum hitpoints. */
data class EntityHealthBar(val current: Int, val maximum: Int, val delay: Int = 0) {
    init {
        require(maximum > 0) { "health-bar maximum must be positive" }
        require(current in 0..maximum) { "health-bar current must be in 0..maximum" }
        require(delay in 0..0x7FFE) { "health-bar delay must fit below the removal sentinel" }
    }
}

/** One graphic attached to an entity's numbered client slot. */
data class EntitySpotAnimation(
    val id: Int,
    val delay: Int = 0,
    val height: Int = 0,
    val slot: Int = 0,
) {
    init {
        require(id in 0 until 0xFFFF) { "spot animation must fit below the unsigned-short null sentinel" }
        require(delay in 0..0xFFFF) { "spot-animation delay must fit an unsigned short" }
        require(height in 0..0xFFFF) { "spot-animation height must fit an unsigned short" }
        require(slot in 0..0xFF) { "spot-animation slot must fit an unsigned byte" }
    }
}

/** Bounded world-thread-owned updates retained until the cycle cleanup phase. */
class EntityInfoUpdates {
    private val hitmarks = ArrayDeque<EntityHitmark>(MAX_HITMARKS)
    private val spotAnimations = LinkedHashMap<Int, EntitySpotAnimation>(MAX_SPOT_ANIMATIONS)
    private var healthBar: EntityHealthBar? = null

    fun showHitmark(hitmark: EntityHitmark): Boolean {
        if (hitmarks.size == MAX_HITMARKS) return false
        hitmarks.addLast(hitmark)
        return true
    }

    fun showHealthBar(value: EntityHealthBar) {
        healthBar = value
    }

    fun playSpotAnimation(value: EntitySpotAnimation): Boolean {
        if (value.slot !in spotAnimations && spotAnimations.size == MAX_SPOT_ANIMATIONS) return false
        spotAnimations[value.slot] = value
        return true
    }

    fun snapshot(): EntityInfoSnapshot? {
        if (hitmarks.isEmpty() && healthBar == null && spotAnimations.isEmpty()) return null
        return EntityInfoSnapshot(hitmarks.toList(), healthBar, spotAnimations.values.toList())
    }

    fun finishCycle() {
        hitmarks.clear()
        spotAnimations.clear()
        healthBar = null
    }

    private companion object {
        const val MAX_HITMARKS = 4
        const val MAX_SPOT_ANIMATIONS = 4
    }
}
