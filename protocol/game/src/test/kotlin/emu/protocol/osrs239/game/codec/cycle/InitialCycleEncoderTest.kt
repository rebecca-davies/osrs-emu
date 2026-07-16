package emu.protocol.osrs239.game.codec.cycle

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.game.codec.audio.AmbienceStopEncoder
import emu.protocol.osrs239.game.codec.camera.CamResetEncoder
import emu.protocol.osrs239.game.codec.camera.CamTargetPlayerEncoder
import emu.protocol.osrs239.game.codec.chat.ChatFilterSettingsEncoder
import emu.protocol.osrs239.game.codec.client.RunClientScriptEncoder
import emu.protocol.osrs239.game.codec.client.SiteSettingsEncoder
import emu.protocol.osrs239.game.codec.component.IfCloseSubEncoder
import emu.protocol.osrs239.game.codec.component.IfOpenSubEncoder
import emu.protocol.osrs239.game.codec.component.IfOpenTopEncoder
import emu.protocol.osrs239.game.codec.component.IfResyncEncoder
import emu.protocol.osrs239.game.codec.component.IfSetHideEncoder
import emu.protocol.osrs239.game.codec.inventory.UpdateInvFullEncoder
import emu.protocol.osrs239.game.codec.npc.HideNpcOpsEncoder
import emu.protocol.osrs239.game.codec.npc.NpcInfoEncoder
import emu.protocol.osrs239.game.codec.player.LogoutEncoder
import emu.protocol.osrs239.game.codec.player.MinimapToggleEncoder
import emu.protocol.osrs239.game.codec.player.ResetAnimsEncoder
import emu.protocol.osrs239.game.codec.player.UpdateRunEnergyEncoder
import emu.protocol.osrs239.game.codec.player.UpdateRunWeightEncoder
import emu.protocol.osrs239.game.codec.player.UpdateStatEncoder
import emu.protocol.osrs239.game.codec.scene.WorldEntityInfoEncoder
import emu.protocol.osrs239.game.codec.varp.VarpLargeEncoder
import emu.protocol.osrs239.game.codec.varp.VarpResetEncoder
import emu.protocol.osrs239.game.codec.varp.VarpSmallEncoder
import emu.protocol.osrs239.game.codec.zone.HideLocOpsEncoder
import emu.protocol.osrs239.game.codec.zone.HideObjOpsEncoder
import emu.protocol.osrs239.game.codec.zone.UpdateZoneFullFollowsEncoder
import emu.protocol.osrs239.game.message.audio.AmbienceStop
import emu.protocol.osrs239.game.message.camera.CamReset
import emu.protocol.osrs239.game.message.camera.CamTargetPlayer
import emu.protocol.osrs239.game.message.chat.ChatFilterSettings
import emu.protocol.osrs239.game.message.client.RunClientScript
import emu.protocol.osrs239.game.message.client.SiteSettings
import emu.protocol.osrs239.game.message.component.IfCloseSub
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.message.component.IfOpenTop
import emu.protocol.osrs239.game.message.component.IfResync
import emu.protocol.osrs239.game.message.component.IfSetHide
import emu.protocol.osrs239.game.message.cycle.PacketGroupStart
import emu.protocol.osrs239.game.message.inventory.UpdateInvFull
import emu.protocol.osrs239.game.message.npc.HideNpcOps
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.player.Logout
import emu.protocol.osrs239.game.message.player.MinimapToggle
import emu.protocol.osrs239.game.message.player.ResetAnims
import emu.protocol.osrs239.game.message.player.UpdateRunEnergy
import emu.protocol.osrs239.game.message.player.UpdateRunWeight
import emu.protocol.osrs239.game.message.player.UpdateStat
import emu.protocol.osrs239.game.message.scene.WorldEntityInfo
import emu.protocol.osrs239.game.message.varp.VarpLarge
import emu.protocol.osrs239.game.message.varp.VarpReset
import emu.protocol.osrs239.game.message.varp.VarpSmall
import emu.protocol.osrs239.game.message.zone.HideLocOps
import emu.protocol.osrs239.game.message.zone.HideObjOps
import emu.protocol.osrs239.game.message.zone.UpdateZoneFullFollows
import emu.protocol.osrs239.game.prot.GameServerProt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Byte vectors are the inverse of rsprox rev-239's reference packet decoders. */
class InitialCycleEncoderTest {
    private fun bytes(vararg values: Int) = values.map(Int::toByte)

    @Test fun `packet group and empty entity info bodies match the rev 239 readers`() {
        assertEquals(bytes(0x09, 0xE1), PacketGroupStartEncoder.encode(NopStreamCipher, PacketGroupStart(2529)).toList())
        assertEquals(bytes(0), WorldEntityInfoEncoder.encode(NopStreamCipher, WorldEntityInfo).toList())
        assertEquals(bytes(0), NpcInfoEncoder.encode(NopStreamCipher, NpcInfo).toList())
    }

    @Test fun `zone and varp transforms invert their rev 239 decoders`() {
        assertEquals(bytes(128 - 48, -56, 0), UpdateZoneFullFollowsEncoder.encode(NopStreamCipher, UpdateZoneFullFollows(56, 48, 0)).toList())
        assertEquals(bytes(127, 0xB4, 0x12), VarpSmallEncoder.encode(NopStreamCipher, VarpSmall(0x1234, -1)).toList())
        assertEquals(bytes(0x12, 0xB4, 0x78, 0x56, 0x34, 0x12), VarpLargeEncoder.encode(NopStreamCipher, VarpLarge(0x1234, 0x12345678)).toList())
    }

    @Test fun `interface open packets use the captured rev 239 alternate byte orders`() {
        assertEquals(bytes(0, 165 + 128), IfOpenTopEncoder.encode(NopStreamCipher, IfOpenTop(165)).toList())
        assertEquals(
            bytes(162 + 128, 0, 165, 0, 2, 0, 1),
            IfOpenSubEncoder.encode(NopStreamCipher, IfOpenSub(165, 2, 162, 1)).toList(),
        )
    }

    @Test fun `interface close packet uses the rev 239 canonical combined id`() {
        assertEquals(GameServerProt.IF_CLOSE_SUB, IfCloseSubEncoder.prot)
        assertEquals(
            bytes(0, 165, 0, 2),
            IfCloseSubEncoder.encode(NopStreamCipher, IfCloseSub(165, 2)).toList(),
        )
    }

    @Test fun `interface close packet validates both packed component halves`() {
        assertEquals(
            bytes(0, 0, 0, 0),
            IfCloseSubEncoder.encode(NopStreamCipher, IfCloseSub(0, 0)).toList(),
        )
        assertEquals(
            bytes(-1, -1, -1, -1),
            IfCloseSubEncoder.encode(NopStreamCipher, IfCloseSub(0xFFFF, 0xFFFF)).toList(),
        )
        assertFailsWith<IllegalArgumentException> { IfCloseSub(-1, 0) }
        assertFailsWith<IllegalArgumentException> { IfCloseSub(0, -1) }
        assertFailsWith<IllegalArgumentException> { IfCloseSub(0x1_0000, 0) }
        assertFailsWith<IllegalArgumentException> { IfCloseSub(0, 0x1_0000) }
    }

    @Test fun `client script body writes its signature then reversed arguments then script id`() {
        assertEquals(
            bytes(0, 0, 0, 4, 0xA2),
            RunClientScriptEncoder.encode(NopStreamCipher, RunClientScript(1186)).toList(),
        )
        assertEquals(
            bytes(
                'i'.code, 's'.code, 'i'.code, 0,
                1, 2, 3, 4,
                'h'.code, 'i'.code, 0,
                0, 0, 0, 7,
                0, 0, 4, 0xA2,
            ),
            RunClientScriptEncoder.encode(
                NopStreamCipher,
                RunClientScript(1186, listOf(7, "hi", 0x01020304)),
            ).toList(),
        )
    }

    @Test fun `interface hide packet uses the rev 239 combined id and boolean transforms`() {
        assertEquals(
            bytes(0xA5, 0, 39, 0, 129),
            IfSetHideEncoder.encode(NopStreamCipher, IfSetHide(165, 39, hidden = true)).toList(),
        )
    }

    @Test fun `interface resync carries top level and all subinterfaces in plain canonical form`() {
        val body = IfResyncEncoder.encode(
            NopStreamCipher,
            IfResync(165, listOf(IfOpenSub(165, 2, 162, 1), IfOpenSub(707, 7, 7, 1))),
        )
        assertEquals(
            bytes(0, 165, 0, 2, 0, 165, 0, 2, 0, 162, 1, 2, 195, 0, 7, 0, 7, 1),
            body.toList(),
        )
    }

    @Test fun `empty inventory and level one stat packets match reference transforms`() {
        assertEquals(
            bytes(0xFF, 0xFF, 0xFA, 0xD0, 0, 94, 0, 0),
            UpdateInvFullEncoder.encode(NopStreamCipher, UpdateInvFull(-1, 64208, 94)).toList(),
        )
        assertEquals(
            bytes(1, 1, 128 + 7, 0, 0, 0, 0),
            UpdateStatEncoder.encode(NopStreamCipher, UpdateStat(stat = 7, currentLevel = 1, invisibleBoostedLevel = 1, experience = 0)).toList(),
        )
    }

    @Test fun `site chat minimap and ambience settings encode neutral captured values`() {
        assertEquals(bytes(0), SiteSettingsEncoder.encode(NopStreamCipher, SiteSettings("")).toList())
        assertEquals(bytes(128, 128), ChatFilterSettingsEncoder.encode(NopStreamCipher, ChatFilterSettings()).toList())
        assertEquals(bytes(0), MinimapToggleEncoder.encode(NopStreamCipher, MinimapToggle()).toList())
        assertEquals(bytes(1), AmbienceStopEncoder.encode(NopStreamCipher, AmbienceStop(fade = true)).toList())
    }

    @Test fun `neutral operation flags and player state match the captured initial cycle`() {
        assertEquals(bytes(0), HideNpcOpsEncoder.encode(NopStreamCipher, HideNpcOps()).toList())
        assertEquals(bytes(0), HideLocOpsEncoder.encode(NopStreamCipher, HideLocOps()).toList())
        assertEquals(bytes(0), HideObjOpsEncoder.encode(NopStreamCipher, HideObjOps()).toList())
        assertEquals(emptyList(), VarpResetEncoder.encode(NopStreamCipher, VarpReset).toList())
        assertEquals(emptyList(), CamResetEncoder.encode(NopStreamCipher, CamReset).toList())
        assertEquals(emptyList(), ResetAnimsEncoder.encode(NopStreamCipher, ResetAnims).toList())
        assertEquals(bytes(0x27, 0x10), UpdateRunEnergyEncoder.encode(NopStreamCipher, UpdateRunEnergy()).toList())
        assertEquals(bytes(0, 0), UpdateRunWeightEncoder.encode(NopStreamCipher, UpdateRunWeight()).toList())
    }

    @Test fun `requested logout is an empty rev 239 logout packet`() {
        assertEquals(GameServerProt.LOGOUT, LogoutEncoder.prot)
        assertEquals(emptyList(), LogoutEncoder.encode(NopStreamCipher, Logout).toList())
    }

    @Test fun `player camera target matches the captured rev 239 camera target reader`() {
        assertEquals(
            bytes(0, 0, 6, 0xC8, 0),
            CamTargetPlayerEncoder.encode(NopStreamCipher, CamTargetPlayer(1736)).toList(),
        )
    }
}
