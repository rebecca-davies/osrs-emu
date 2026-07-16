package emu.server.game.network.output.stat

import emu.game.content.player.Skill
import emu.protocol.osrs239.game.message.player.UpdateStat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerStatOutputTest {
    @Test
    fun `new character has authentic hitpoints and otherwise level one stats`() {
        val stats = PlayerStatOutput.initialMessages()

        assertEquals(Skill.entries.size, stats.size)
        assertEquals(UpdateStat(Skill.HITPOINTS.id, 10, 10, 1_154), stats[Skill.HITPOINTS.id])
        assertTrue(
            stats.all { stat ->
                stat.stat == Skill.HITPOINTS.id || stat == UpdateStat(stat.stat, 1, 1, 0)
            },
        )
    }
}
