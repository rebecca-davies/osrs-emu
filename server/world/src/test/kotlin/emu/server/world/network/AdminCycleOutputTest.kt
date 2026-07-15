package emu.server.world.network

import emu.game.cycle.CycleProfileSnapshot
import emu.persistence.account.PlayerRank
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminCycleOutputTest {
    @Test
    fun `cycle profile is visible only to administrators`() {
        val snapshot = CycleProfileSnapshot(50, 2_000_000, 8_000_000, 1, 30_000_000_000)

        assertNull(AdminCycleOutput.message(PlayerRank.PLAYER, snapshot))
        assertNull(AdminCycleOutput.message(PlayerRank.MODERATOR, snapshot))
        val report = requireNotNull(AdminCycleOutput.message(PlayerRank.ADMINISTRATOR, snapshot))
        assertTrue("avg=2.0ms" in report.text)
        assertTrue("max=8.0ms" in report.text)
    }
}
