package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Supplies the account's serialized website/client settings string; empty means neutral defaults. */
data class SiteSettings(val settings: String = "") : OutgoingMessage
