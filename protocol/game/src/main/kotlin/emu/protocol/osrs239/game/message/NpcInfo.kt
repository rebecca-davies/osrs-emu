package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Empty rev-239 small-coordinate NPC update for a world with no emulated NPCs yet. */
data object NpcInfo : OutgoingMessage
