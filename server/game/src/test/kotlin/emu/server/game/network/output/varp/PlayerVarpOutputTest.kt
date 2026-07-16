package emu.server.game.network.output.varp

import emu.game.content.player.PlayerVarpCatalog
import emu.game.map.Tile
import emu.game.player.Player
import emu.protocol.osrs239.game.message.varp.VarpLarge
import emu.protocol.osrs239.game.message.varp.VarpSmall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerVarpOutputTest {
    @Test
    fun `fresh account varps select the exact small and large wire forms`() {
        val varps = Player(Tile(3_200, 3_200)).varps
        val messages = PlayerVarpOutput.loginSync(varps)

        assertTrue(messages.contains(VarpSmall(PlayerVarpCatalog.RUN_MODE.id, 0)))
        assertTrue(
            messages.contains(
                VarpLarge(PlayerVarpCatalog.HAS_DISPLAY_NAME.baseVar.id, Int.MIN_VALUE),
            ),
        )
    }
}
