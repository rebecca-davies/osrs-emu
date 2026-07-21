package emu.server.game.world.player.cheat

import emu.server.session.account.AccountPrivilege

/** Immutable command-name dispatch for Jagex developer-console input. */
class PlayerCheatRepository internal constructor(
    private val handlers: Map<String, PlayerCheatHandler>,
) {
    internal fun execute(input: String, privilege: AccountPrivilege): String? {
        val start = input.indexOfFirst { !it.isWhitespace() }
        if (start == -1) return null
        val separator = input.indexOfFirstFrom(start, Char::isWhitespace)
        val commandEnd = if (separator == -1) input.length else separator
        val command = input.substring(start, commandEnd).lowercase()
        val arguments = if (separator == -1) "" else input.substring(separator + 1).trim()
        return handlers[command]?.execute(privilege, arguments)
    }
}

/** Handles one registered developer-console command on the world thread. */
internal fun interface PlayerCheatHandler {
    fun execute(privilege: AccountPrivilege, arguments: String): String?
}

/** Builds a command repository while rejecting ambiguous duplicate names. */
internal class PlayerCheatRepositoryBuilder {
    private val handlers = HashMap<String, PlayerCheatHandler>()

    fun bind(command: String, handler: PlayerCheatHandler): PlayerCheatRepositoryBuilder {
        val name = command.lowercase()
        require(name.isNotBlank() && name.none(Char::isWhitespace)) { "invalid player cheat name" }
        require(handlers.putIfAbsent(name, handler) == null) { "duplicate player cheat $name" }
        return this
    }

    fun build(): PlayerCheatRepository = PlayerCheatRepository(handlers.toMap())
}

private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
    for (index in start until length) {
        if (predicate(this[index])) return index
    }
    return -1
}
