package emu.protocol.osrs239.game

import emu.netcore.codec.MessageEncoder
import emu.protocol.osrs239.game.codec.PlayerInfoEncoder
import emu.protocol.osrs239.game.codec.RebuildNormalEncoder
import emu.protocol.osrs239.game.codec.ServerTickEndEncoder
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Declares every game-domain wire codec as a Koin singleton bound to [MessageEncoder], collected
 * the same way as [emu.protocol.osrs239.js5.js5Module] (CLAUDE.md §5a addendum).
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b).
 */
val gameModule = module {
    single(named("game.rebuildNormal")) { RebuildNormalEncoder } bind MessageEncoder::class
    single(named("game.playerInfo")) { PlayerInfoEncoder } bind MessageEncoder::class
    single(named("game.serverTickEnd")) { ServerTickEndEncoder } bind MessageEncoder::class
}
