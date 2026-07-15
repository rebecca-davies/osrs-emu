package emu.protocol.osrs239.game

import emu.netcore.codec.CodecRepository
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.codec.MessageDecoder
import emu.netcore.codec.MessageEncoder
import emu.protocol.osrs239.game.codec.AmbienceStopEncoder
import emu.protocol.osrs239.game.codec.CamResetEncoder
import emu.protocol.osrs239.game.codec.CamTargetPlayerEncoder
import emu.protocol.osrs239.game.codec.ChatFilterSettingsEncoder
import emu.protocol.osrs239.game.codec.ChatFilterPrivateEncoder
import emu.protocol.osrs239.game.codec.HideLocOpsEncoder
import emu.protocol.osrs239.game.codec.HideNpcOpsEncoder
import emu.protocol.osrs239.game.codec.HideObjOpsEncoder
import emu.protocol.osrs239.game.codec.IfOpenSubEncoder
import emu.protocol.osrs239.game.codec.IfOpenTopEncoder
import emu.protocol.osrs239.game.codec.IfResyncEncoder
import emu.protocol.osrs239.game.codec.IfSetHideEncoder
import emu.protocol.osrs239.game.codec.IfButtonXDecoder
import emu.protocol.osrs239.game.codec.LogoutEncoder
import emu.protocol.osrs239.game.codec.MessageGameEncoder
import emu.protocol.osrs239.game.codec.MinimapToggleEncoder
import emu.protocol.osrs239.game.codec.MoveGameClickDecoder
import emu.protocol.osrs239.game.codec.MessagePublicDecoder
import emu.protocol.osrs239.game.codec.SetChatFilterSettingsDecoder
import emu.protocol.osrs239.game.codec.NpcInfoEncoder
import emu.protocol.osrs239.game.codec.PacketGroupStartEncoder
import emu.protocol.osrs239.game.codec.PlayerInfoEncoder
import emu.protocol.osrs239.game.codec.RebuildLoginEncoder
import emu.protocol.osrs239.game.codec.RebuildNormalEncoder
import emu.protocol.osrs239.game.codec.ResetAnimsEncoder
import emu.protocol.osrs239.game.codec.RunClientScriptEncoder
import emu.protocol.osrs239.game.codec.ServerTickEndEncoder
import emu.protocol.osrs239.game.codec.SetActiveWorldEncoder
import emu.protocol.osrs239.game.codec.SetNpcUpdateOriginEncoder
import emu.protocol.osrs239.game.codec.SiteSettingsEncoder
import emu.protocol.osrs239.game.codec.UpdateInvFullEncoder
import emu.protocol.osrs239.game.codec.UpdateRunEnergyEncoder
import emu.protocol.osrs239.game.codec.UpdateRunWeightEncoder
import emu.protocol.osrs239.game.codec.UpdateStatEncoder
import emu.protocol.osrs239.game.codec.UpdateZoneFullFollowsEncoder
import emu.protocol.osrs239.game.codec.VarpLargeEncoder
import emu.protocol.osrs239.game.codec.VarpResetEncoder
import emu.protocol.osrs239.game.codec.VarpSmallEncoder
import emu.protocol.osrs239.game.codec.WorldEntityInfoEncoder

/** Rev-239 game-domain codecs. */
object GameCodecs {
    val decoders: List<MessageDecoder<*>> = listOf(
        MoveGameClickDecoder,
        IfButtonXDecoder,
        MessagePublicDecoder,
        SetChatFilterSettingsDecoder,
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
fun buildGameCodecRepository(): CodecRepository = CodecRepositoryBuilder()
    .also { builder ->
        GameCodecs.decoders.forEach(builder::bindDecoder)
        GameCodecs.encoders.forEach(builder::bindEncoder)
    }
    .build()
