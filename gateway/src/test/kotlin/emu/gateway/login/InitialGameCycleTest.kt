package emu.gateway.login

import emu.game.cycle.CycleProfileSnapshot
import emu.protocol.osrs239.game.message.IfOpenSub
import emu.protocol.osrs239.game.message.CamTargetPlayer
import emu.protocol.osrs239.game.message.MessageGame
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.SetActiveWorld
import emu.protocol.osrs239.game.message.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.message.UpdateZoneFullFollows
import emu.protocol.osrs239.game.message.UpdateStat
import emu.protocol.osrs239.game.message.VarpSmall
import emu.protocol.osrs239.game.message.VarpLarge
import emu.persistence.PlayerPosition
import emu.persistence.PlayerRank
import emu.protocol.osrs239.game.message.WorldEntityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InitialGameCycleTest {
    @Test fun `fresh account starts walking and can still use unlimited run`() {
        val varps = initialPlayerVarps()
        val movement = initialPlayerMovement(PlayerPosition(3222, 3218, 0), runEnabled = false)

        assertFalse(movement.runEnabled)
        assertTrue(initialAccountVarps(varps).contains(VarpSmall(173, 0)))
    }

    @Test fun `cycle profile chat is visible only to administrators`() {
        val snapshot = CycleProfileSnapshot(50, 2_000_000, 8_000_000, 1, 30_000_000_000)

        assertEquals(null, adminCycleReport(PlayerRank.PLAYER, snapshot))
        assertEquals(null, adminCycleReport(PlayerRank.MODERATOR, snapshot))
        val report = requireNotNull(adminCycleReport(PlayerRank.ADMINISTRATOR, snapshot))
        assertTrue("avg=2.0ms" in report.message)
        assertTrue("max=8.0ms" in report.message)
    }

    @Test fun `authenticated display name is used by local-player appearance and chat identity`() {
        assertEquals("Rebecca_Bird", playerAppearance("Rebecca_Bird").name)
        assertTrue(initialAccountVarps().contains(VarpLarge(1737, Int.MIN_VALUE)))
    }

    @Test fun `new character starts with authentic hitpoints and otherwise level one stats`() {
        val stats = initialStatMessages()

        assertEquals(23, stats.size)
        assertEquals(UpdateStat(stat = 3, currentLevel = 10, invisibleBoostedLevel = 10, experience = 1_154), stats[3])
        assertTrue(stats.withIndex().all { (index, stat) ->
            index == 3 || stat == UpdateStat(index, 1, 1, 0)
        })
    }

    @Test fun `initial world group matches capture order and clears the captured 7 by 7 zone window`() {
        val group = initialWorldGroup(
            PlayerAppearance(),
            localPlayerIndex = 1736,
            localPlayerX = 54,
            localPlayerY = 50,
        )

        assertIs<SetActiveWorld>(group[0])
        assertEquals(SetNpcUpdateOrigin(54, 50), group[1])
        assertEquals(WorldEntityInfo, group[2])
        assertIs<PlayerInfo>(group[3])
        assertEquals(NpcInfo, group[4])

        val zones = group.drop(5).dropLast(1).map { assertIs<UpdateZoneFullFollows>(it) }
        assertEquals(49, zones.size)
        assertEquals(UpdateZoneFullFollows(48, 48), zones.first())
        assertEquals(UpdateZoneFullFollows(72, 24), zones.last())
        assertEquals(49, zones.map { it.zoneX to it.zoneZ }.toSet().size)
        assertTrue(zones.all { it.zoneX in 24..72 && it.zoneZ in 24..72 && it.level == 0 })
        assertEquals(CamTargetPlayer(1736), group.last())
    }

    @Test fun `default rev 239 frame contains permanent overlays without capture account welcome modal`() {
        val subs = initialFrameSubInterfaces()

        assertEquals(25, subs.size)
        assertEquals(IfOpenSub(161, 96, 162), subs.first())
        assertTrue(subs.none { it.type == IfOpenSub.MODAL || it.interfaceId == 378 })
        assertTrue(subs.contains(IfOpenSub(707, 7, 7)))
    }

    @Test fun `login notice is the plain 'Welcome to RuneScape' game message`() {
        assertEquals(
            listOf(MessageGame(MessageGame.GAME_MESSAGE, "Welcome to RuneScape.")),
            loginNoticeMessages(),
        )
    }

    @Test fun `account neutral frame opens the captured post welcome game top level directly`() {
        val messages = initialFrameMessages()
        val topIndex = messages.indexOfFirst { it is emu.protocol.osrs239.game.message.IfOpenTop }

        assertTrue(topIndex >= 0)
        assertEquals(emu.protocol.osrs239.game.message.IfOpenTop(161), messages[topIndex])
        assertIs<IfOpenSub>(messages[topIndex + 1])
    }
}
