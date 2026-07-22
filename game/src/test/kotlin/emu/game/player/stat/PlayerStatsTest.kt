package emu.game.player.stat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerStatsTest {
    @Test
    fun `beta characters start maxed with RuneScape experience thresholds`() {
        val stats = PlayerStats()

        assertTrue(stats.loginSync().all { it.baseLevel == 99 && it.currentLevel == 99 })
        assertTrue(stats.loginSync().all { it.experience == 13_034_431 })
        assertEquals(126, stats.combatLevel())
    }

    @Test
    fun `level edits coalesce into protocol ordered client updates`() {
        val stats = PlayerStats()

        stats.setLevel(Skill.MAGIC, 70)
        stats.setLevel(Skill.ATTACK, 50)
        stats.setLevel(Skill.MAGIC, 80)

        assertEquals(
            listOf(
                SkillStat(Skill.ATTACK, 50, 50, 101_333),
                SkillStat(Skill.MAGIC, 80, 80, 1_986_068),
            ),
            stats.drainClientUpdates(),
        )
        assertTrue(stats.drainClientUpdates().isEmpty())
    }
}
