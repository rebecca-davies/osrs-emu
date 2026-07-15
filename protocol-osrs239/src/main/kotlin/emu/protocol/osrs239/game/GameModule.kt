package emu.protocol.osrs239.game

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
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Declares every game-domain wire codec as a Koin singleton bound to [MessageEncoder], collected
 * the same way as [emu.protocol.osrs239.js5.js5Module] (CLAUDE.md §5a addendum).
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b).
 */
val gameModule = module {
    single(named("game.moveGameClick")) { MoveGameClickDecoder } bind MessageDecoder::class
    single(named("game.ifButtonX")) { IfButtonXDecoder } bind MessageDecoder::class
    single(named("game.messagePublic")) { MessagePublicDecoder } bind MessageDecoder::class
    single(named("game.setChatFilterSettings")) { SetChatFilterSettingsDecoder } bind MessageDecoder::class
    single(named("game.logout")) { LogoutEncoder } bind MessageEncoder::class
    single(named("game.siteSettings")) { SiteSettingsEncoder } bind MessageEncoder::class
    single(named("game.chatFilterSettings")) { ChatFilterSettingsEncoder } bind MessageEncoder::class
    single(named("game.chatFilterPrivate")) { ChatFilterPrivateEncoder } bind MessageEncoder::class
    single(named("game.hideNpcOps")) { HideNpcOpsEncoder } bind MessageEncoder::class
    single(named("game.hideLocOps")) { HideLocOpsEncoder } bind MessageEncoder::class
    single(named("game.hideObjOps")) { HideObjOpsEncoder } bind MessageEncoder::class
    single(named("game.varpReset")) { VarpResetEncoder } bind MessageEncoder::class
    single(named("game.varpSmall")) { VarpSmallEncoder } bind MessageEncoder::class
    single(named("game.varpLarge")) { VarpLargeEncoder } bind MessageEncoder::class
    single(named("game.rebuildLogin")) { RebuildLoginEncoder } bind MessageEncoder::class
    single(named("game.rebuildNormal")) { RebuildNormalEncoder } bind MessageEncoder::class
    single(named("game.packetGroupStart")) { PacketGroupStartEncoder } bind MessageEncoder::class
    single(named("game.messageGame")) { MessageGameEncoder } bind MessageEncoder::class
    single(named("game.setActiveWorld")) { SetActiveWorldEncoder } bind MessageEncoder::class
    single(named("game.worldEntityInfo")) { WorldEntityInfoEncoder } bind MessageEncoder::class
    single(named("game.playerInfo")) { PlayerInfoEncoder } bind MessageEncoder::class
    single(named("game.setNpcUpdateOrigin")) { SetNpcUpdateOriginEncoder } bind MessageEncoder::class
    single(named("game.npcInfo")) { NpcInfoEncoder } bind MessageEncoder::class
    single(named("game.updateZoneFullFollows")) { UpdateZoneFullFollowsEncoder } bind MessageEncoder::class
    single(named("game.updateInvFull")) { UpdateInvFullEncoder } bind MessageEncoder::class
    single(named("game.ifOpenTop")) { IfOpenTopEncoder } bind MessageEncoder::class
    single(named("game.ifOpenSub")) { IfOpenSubEncoder } bind MessageEncoder::class
    single(named("game.ifResync")) { IfResyncEncoder } bind MessageEncoder::class
    single(named("game.ifSetHide")) { IfSetHideEncoder } bind MessageEncoder::class
    single(named("game.runClientScript")) { RunClientScriptEncoder } bind MessageEncoder::class
    single(named("game.camReset")) { CamResetEncoder } bind MessageEncoder::class
    single(named("game.camTargetPlayer")) { CamTargetPlayerEncoder } bind MessageEncoder::class
    single(named("game.ambienceStop")) { AmbienceStopEncoder } bind MessageEncoder::class
    single(named("game.updateStat")) { UpdateStatEncoder } bind MessageEncoder::class
    single(named("game.updateRunWeight")) { UpdateRunWeightEncoder } bind MessageEncoder::class
    single(named("game.updateRunEnergy")) { UpdateRunEnergyEncoder } bind MessageEncoder::class
    single(named("game.resetAnims")) { ResetAnimsEncoder } bind MessageEncoder::class
    single(named("game.minimapToggle")) { MinimapToggleEncoder } bind MessageEncoder::class
    single(named("game.serverTickEnd")) { ServerTickEndEncoder } bind MessageEncoder::class
}
