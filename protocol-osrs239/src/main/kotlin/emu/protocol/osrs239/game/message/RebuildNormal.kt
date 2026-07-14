package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * The local player's absolute tile coordinates. Encodes as the first post-login game packet:
 * GPI-init (local player position + zeroed reference coords for every other player slot, §4a)
 * followed by the 13x13 base-zone grid centred on this position (§3b).
 * See docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §3/§4a.
 *
 * @param plane 0..3
 * @param x absolute world X, a 14-bit value (0..16383)
 * @param y absolute world Y, a 14-bit value (0..16383)
 */
data class RebuildNormal(val plane: Int, val x: Int, val y: Int) : OutgoingMessage
