package emu.protocol.osrs239.game.message.scene

import emu.transport.message.OutgoingMessage

/**
 * The local player's absolute tile coordinates. Encodes as the first post-login game packet:
 * GPI-init (local player position + zeroed reference coordinates for every other player slot)
 * followed by the centre-zone coordinates for the 13x13 build area.
 *
 * @param plane 0..3
 * @param x absolute world X, a 14-bit value (0..16383)
 * @param y absolute world Y, a 14-bit value (0..16383)
 * @param localPlayerIndex the local player's slot in the 2048-entry player array (`client.di`, set
 *   from the login-info block). The GPI-init loop skips exactly this slot when reading the 18-bit
 *   reference coords (`dy.af`), so the encoder must emit one fewer slot; it must match the `di`
 *   sent in the login-info trailer or the bit stream length (and hence the trailing zone bytes)
 *   misaligns against what the client reads.
 */
data class RebuildLogin(
    val plane: Int,
    val x: Int,
    val y: Int,
    val localPlayerIndex: Int = 1,
) : OutgoingMessage
