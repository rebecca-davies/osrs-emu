package emu.protocol.osrs239.game.message.client

import emu.transport.message.OutgoingMessage

/** Supplies the account's serialized website/client settings string; empty means neutral defaults. */
data class SiteSettings(val settings: String = "") : OutgoingMessage
