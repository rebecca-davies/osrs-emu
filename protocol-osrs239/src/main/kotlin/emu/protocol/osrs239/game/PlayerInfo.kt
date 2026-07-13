package emu.protocol.osrs239.game

import emu.netcore.message.OutgoingMessage

/**
 * PLAYER_INFO (opcode 28) for the minimal single-local-player case: no other players tracked yet,
 * local player not moving, extended-info present with just the appearance block so the avatar
 * model draws (docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §4b/§4c).
 *
 * **CONFIDENCE: MEDIUM** on the overall shape (see [PlayerInfoEncoder] for the field-by-field
 * breakdown) — `dy.ae`/`dy.uq`/`dy.yq`/`dy.ax` are CFR-mangled in the decompile, so this is the
 * best-supported reconstruction, not a byte-verified one. A later task must iterate this against
 * the real client, using the `dy.ae` bytes-consumed assertion (`dy.java:120`) as the falsifier.
 */
data class PlayerInfo(val appearance: PlayerAppearance) : OutgoingMessage
