package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * PLAYER_INFO (opcode 28) for the minimal single-local-player case: no other players are updated
 * or added, and the local player is stationary. This is the standard OSRS "GPI" bit stream
 * (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b/§4c), reconstructed from the
 * rev-239 decompile (`dy.ae`/`dy.uq`/`dy.ax`/`dy.ab`) 2026-07-14.
 *
 * @param appearance if non-null, the local player is flagged for an extended-info **appearance**
 *   block so its avatar model draws. If null, the local player has no extended info at all (the
 *   avatar has no model / is invisible) but the packet is still a byte-exact, in-world-stable
 *   GPI update. The appearance block in rev 239 is a complex serialized sub-buffer (see
 *   [emu.protocol.osrs239.game.codec.PlayerInfoEncoder]); until it is reproduced byte-for-byte,
 *   send `null` to keep the client in-world with terrain rendered.
 */
data class PlayerInfo(val appearance: PlayerAppearance? = null) : OutgoingMessage
