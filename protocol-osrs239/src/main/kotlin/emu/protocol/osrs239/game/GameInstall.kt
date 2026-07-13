package emu.protocol.osrs239.game

import emu.netcore.codec.CodecRepositoryBuilder

/**
 * Registers every game-domain wire codec in one small, greppable place (CLAUDE.md §5a).
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b).
 */
fun CodecRepositoryBuilder.installGame(): CodecRepositoryBuilder = this
    .bindEncoder(RebuildNormalEncoder)
    .bindEncoder(PlayerInfoEncoder)
