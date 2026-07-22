package emu.game.player.stat

/** One authoritative skill value ready for client synchronization. */
data class SkillStat(
    val skill: Skill,
    val currentLevel: Int,
    val baseLevel: Int,
    val experience: Int,
)
