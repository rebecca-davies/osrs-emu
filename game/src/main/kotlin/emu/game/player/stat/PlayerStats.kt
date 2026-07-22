package emu.game.player.stat

import kotlin.math.floor
import kotlin.math.max

/** World-thread-owned levels and experience for one player. */
class PlayerStats(initialLevel: Int = SkillExperience.MAX_LEVEL) {
    private val currentLevels = IntArray(Skill.entries.size) { initialLevel }
    private val baseLevels = IntArray(Skill.entries.size) { initialLevel }
    private val experience = IntArray(Skill.entries.size) { SkillExperience.forLevel(initialLevel) }
    private var pendingClientMask = 0

    var combatRevision: Int = 0
        private set

    init {
        require(initialLevel in SkillExperience.MIN_LEVEL..SkillExperience.MAX_LEVEL) {
            "initial skill level must be in ${SkillExperience.MIN_LEVEL}..${SkillExperience.MAX_LEVEL}"
        }
        require(Skill.entries.size <= Int.SIZE_BITS) { "skill dirty tracking exceeds one integer" }
        require(Skill.entries.withIndex().all { (index, skill) -> skill.id == index }) {
            "skills must remain in client protocol order"
        }
    }

    operator fun get(skill: Skill): SkillStat = value(skill)

    /** Replaces a skill's base/current level and experience at that level's threshold. */
    fun setLevel(skill: Skill, level: Int) {
        require(level in SkillExperience.MIN_LEVEL..SkillExperience.MAX_LEVEL) {
            "skill level must be in ${SkillExperience.MIN_LEVEL}..${SkillExperience.MAX_LEVEL}"
        }
        val id = skill.id
        val levelExperience = SkillExperience.forLevel(level)
        if (
            currentLevels[id] == level &&
            baseLevels[id] == level &&
            experience[id] == levelExperience
        ) {
            return
        }
        currentLevels[id] = level
        baseLevels[id] = level
        experience[id] = levelExperience
        pendingClientMask = pendingClientMask or (1 shl id)
        if (skill in COMBAT_SKILLS) combatRevision++
    }

    /** Complete deterministic state for initial login synchronization. */
    fun loginSync(): List<SkillStat> = Skill.entries.map(::value)

    /** Removes changed skills in client protocol order. */
    fun drainClientUpdates(): List<SkillStat> {
        var remaining = pendingClientMask
        if (remaining == 0) return emptyList()
        pendingClientMask = 0
        return buildList(Integer.bitCount(remaining)) {
            while (remaining != 0) {
                val id = Integer.numberOfTrailingZeros(remaining)
                add(value(Skill.entries[id]))
                remaining = remaining and (1 shl id).inv()
            }
        }
    }

    /** Combat level derived from the player's authoritative base levels. */
    fun combatLevel(): Int {
        val defence = baseLevels[Skill.DEFENCE.id]
        val hitpoints = baseLevels[Skill.HITPOINTS.id]
        val prayer = baseLevels[Skill.PRAYER.id]
        val base = 0.25 * (defence + hitpoints + floor(prayer / 2.0))
        val melee = 0.325 * (baseLevels[Skill.ATTACK.id] + baseLevels[Skill.STRENGTH.id])
        val ranged = 0.325 * floor(baseLevels[Skill.RANGED.id] * 1.5)
        val magic = 0.325 * floor(baseLevels[Skill.MAGIC.id] * 1.5)
        return floor(base + max(melee, max(ranged, magic))).toInt()
    }

    private fun value(skill: Skill): SkillStat {
        val id = skill.id
        return SkillStat(skill, currentLevels[id], baseLevels[id], experience[id])
    }

    private companion object {
        val COMBAT_SKILLS =
            setOf(
                Skill.ATTACK,
                Skill.DEFENCE,
                Skill.STRENGTH,
                Skill.HITPOINTS,
                Skill.RANGED,
                Skill.PRAYER,
                Skill.MAGIC,
            )
    }
}
