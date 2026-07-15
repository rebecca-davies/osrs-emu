package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Empty rev-239 root-world entity update; establishes that no dynamic world entities are active. */
data object WorldEntityInfo : OutgoingMessage
