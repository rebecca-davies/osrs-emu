package emu.server.game.world.player.command

import emu.game.player.Player
import emu.server.session.account.AccountPrivilege

/** Declares the developer-console name selected for a player command. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Command(val name: String)

/** Declares the minimum authenticated account privilege required to run a player command. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Role(val value: AccountPrivilege)

/** Handles one explicitly registered player command on the world thread. */
fun interface PlayerCommand {
    fun execute(player: Player, arguments: String): String?
}
