package emu.server.world.network

import emu.protocol.osrs239.game.message.CamTargetPlayer
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.SetActiveWorld
import emu.protocol.osrs239.game.message.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.message.UpdateZoneFullFollows
import emu.protocol.osrs239.game.message.WorldEntityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InitialWorldOutputTest {
    @Test
    fun `initial group uses protocol order and clears the seven by seven zone window`() {
        val group = InitialWorldOutput.messages(PlayerAppearance(), 1736, 54, 50)

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
}
