package emu.persistence.chat

/** Audited chat channel independent of revision-specific packet types. */
enum class ChatChannel(val id: Int) {
    PUBLIC(0),
}
