package emu.server.game.network.output.stat

import emu.game.player.stat.Skill
import emu.game.player.stat.PlayerStats
import emu.protocol.osrs239.game.message.player.UpdateStat
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerStatOutputTest {
    @Test
    fun `beta character stat state maps to revision 239 messages`() {
        val playerStats = PlayerStats()
        playerStats.setLevel(Skill.HITPOINTS, 70)
        val stats = PlayerStatOutput.messages(playerStats.loginSync())

        assertEquals(Skill.entries.size, stats.size)
        assertEquals(
            UpdateStat(Skill.HITPOINTS.id, 70, 70, 737_627),
            stats[Skill.HITPOINTS.id],
        )
    }
}
