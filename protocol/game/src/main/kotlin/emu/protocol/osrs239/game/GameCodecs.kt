package emu.protocol.osrs239.game

import emu.protocol.osrs239.game.codec.audio.AmbienceStopEncoder
import emu.protocol.osrs239.game.codec.camera.CamResetEncoder
import emu.protocol.osrs239.game.codec.camera.CamTargetPlayerEncoder
import emu.protocol.osrs239.game.codec.chat.ChatFilterPrivateEncoder
import emu.protocol.osrs239.game.codec.chat.ChatFilterSettingsEncoder
import emu.protocol.osrs239.game.codec.chat.MessageGameEncoder
import emu.protocol.osrs239.game.codec.chat.MessagePublicDecoder
import emu.protocol.osrs239.game.codec.chat.SetChatFilterSettingsDecoder
import emu.protocol.osrs239.game.codec.client.ClientCheatDecoder
import emu.protocol.osrs239.game.codec.client.EventAppletFocusDecoder
import emu.protocol.osrs239.game.codec.client.NoTimeoutDecoder
import emu.protocol.osrs239.game.codec.client.RunClientScriptEncoder
import emu.protocol.osrs239.game.codec.client.SiteSettingsEncoder
import emu.protocol.osrs239.game.codec.component.IfButtonXDecoder
import emu.protocol.osrs239.game.codec.component.IfCloseSubEncoder
import emu.protocol.osrs239.game.codec.component.IfOpenSubEncoder
import emu.protocol.osrs239.game.codec.component.IfOpenTopEncoder
import emu.protocol.osrs239.game.codec.component.IfResyncEncoder
import emu.protocol.osrs239.game.codec.component.IfSetHideEncoder
import emu.protocol.osrs239.game.codec.cycle.PacketGroupStartEncoder
import emu.protocol.osrs239.game.codec.cycle.ServerTickEndEncoder
import emu.protocol.osrs239.game.codec.inventory.UpdateInvFullEncoder
import emu.protocol.osrs239.game.codec.movement.MoveGameClickDecoder
import emu.protocol.osrs239.game.codec.npc.HideNpcOpsEncoder
import emu.protocol.osrs239.game.codec.npc.NpcInfoEncoder
import emu.protocol.osrs239.game.codec.npc.SetNpcUpdateOriginEncoder
import emu.protocol.osrs239.game.codec.player.LogoutEncoder
import emu.protocol.osrs239.game.codec.player.MinimapToggleEncoder
import emu.protocol.osrs239.game.codec.player.ResetAnimsEncoder
import emu.protocol.osrs239.game.codec.player.UpdateRunEnergyEncoder
import emu.protocol.osrs239.game.codec.player.UpdateRunWeightEncoder
import emu.protocol.osrs239.game.codec.player.UpdateStatEncoder
import emu.protocol.osrs239.game.codec.playerinfo.PlayerInfoEncoder
import emu.protocol.osrs239.game.codec.scene.RebuildLoginEncoder
import emu.protocol.osrs239.game.codec.scene.RebuildNormalEncoder
import emu.protocol.osrs239.game.codec.scene.SetActiveWorldEncoder
import emu.protocol.osrs239.game.codec.scene.WorldEntityInfoEncoder
import emu.protocol.osrs239.game.codec.varp.VarpLargeEncoder
import emu.protocol.osrs239.game.codec.varp.VarpResetEncoder
import emu.protocol.osrs239.game.codec.varp.VarpSmallEncoder
import emu.protocol.osrs239.game.codec.zone.HideLocOpsEncoder
import emu.protocol.osrs239.game.codec.zone.HideObjOpsEncoder
import emu.protocol.osrs239.game.codec.zone.UpdateZoneFullFollowsEncoder
import emu.transport.codec.CodecRepository
import emu.transport.codec.CodecRepositoryBuilder
import emu.transport.codec.MessageDecoder
import emu.transport.codec.MessageEncoder

/** Rev-239 game-domain codecs. */
object GameCodecs {
    val decoders: List<MessageDecoder<*>> = listOf(
        MoveGameClickDecoder,
        IfButtonXDecoder,
        ClientCheatDecoder,
        MessagePublicDecoder,
        SetChatFilterSettingsDecoder,
        EventAppletFocusDecoder,
        NoTimeoutDecoder,
    )

    val encoders: List<MessageEncoder<*>> = listOf(
        LogoutEncoder,
        SiteSettingsEncoder,
        ChatFilterSettingsEncoder,
        ChatFilterPrivateEncoder,
        HideNpcOpsEncoder,
        HideLocOpsEncoder,
        HideObjOpsEncoder,
        VarpResetEncoder,
        VarpSmallEncoder,
        VarpLargeEncoder,
        RebuildLoginEncoder,
        RebuildNormalEncoder,
        PacketGroupStartEncoder,
        MessageGameEncoder,
        SetActiveWorldEncoder,
        WorldEntityInfoEncoder,
        PlayerInfoEncoder,
        SetNpcUpdateOriginEncoder,
        NpcInfoEncoder,
        UpdateZoneFullFollowsEncoder,
        UpdateInvFullEncoder,
        IfOpenTopEncoder,
        IfOpenSubEncoder,
        IfCloseSubEncoder,
        IfResyncEncoder,
        IfSetHideEncoder,
        RunClientScriptEncoder,
        CamResetEncoder,
        CamTargetPlayerEncoder,
        AmbienceStopEncoder,
        UpdateStatEncoder,
        UpdateRunWeightEncoder,
        UpdateRunEnergyEncoder,
        ResetAnimsEncoder,
        MinimapToggleEncoder,
        ServerTickEndEncoder,
    )
}

/** Builds an immutable repository containing only rev-239 game codecs. */
fun buildGameCodecRepository(): CodecRepository =
    CodecRepositoryBuilder()
        .also { builder ->
            GameCodecs.decoders.forEach(builder::bindDecoder)
            GameCodecs.encoders.forEach(builder::bindEncoder)
        }.build()
