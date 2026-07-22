package emu.server.game.world.player.command

import emu.game.player.Player
import emu.server.session.account.AccountPrivilege

/** Immutable name and role dispatch for registered player commands. */
class PlayerCommandRepository internal constructor(
    private val commands: Map<String, RegisteredPlayerCommand>,
) {
    internal fun execute(
        input: String,
        player: Player,
        privilege: AccountPrivilege,
    ): String? {
        val start = input.indexOfFirst { !it.isWhitespace() }
        if (start == -1) return null
        val separator = input.indexOfFirstFrom(start, Char::isWhitespace)
        val commandEnd = if (separator == -1) input.length else separator
        val name = input.substring(start, commandEnd).lowercase()
        val registered = commands[name] ?: return null
        if (privilege.level < registered.minimumRole.level) return null
        val arguments = if (separator == -1) "" else input.substring(separator + 1).trim()
        return registered.command.execute(player, arguments)
    }
}

internal class PlayerCommandRepositoryBuilder {
    private val commands = HashMap<String, RegisteredPlayerCommand>()

    fun bind(command: PlayerCommand): PlayerCommandRepositoryBuilder {
        val type = command.javaClass
        val definition = requireNotNull(type.getAnnotation(Command::class.java)) {
            "player command ${type.name} is missing @Command"
        }
        val role = requireNotNull(type.getAnnotation(Role::class.java)) {
            "player command ${type.name} is missing @Role"
        }
        val name = definition.name.lowercase()
        require(name.isNotBlank() && name.none(Char::isWhitespace)) { "invalid player command name" }
        require(commands.putIfAbsent(name, RegisteredPlayerCommand(command, role.value)) == null) {
            "duplicate player command $name"
        }
        return this
    }

    fun build(): PlayerCommandRepository = PlayerCommandRepository(commands.toMap())
}

internal data class RegisteredPlayerCommand(
    val command: PlayerCommand,
    val minimumRole: AccountPrivilege,
)

private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
    for (index in start until length) {
        if (predicate(this[index])) return index
    }
    return -1
}
