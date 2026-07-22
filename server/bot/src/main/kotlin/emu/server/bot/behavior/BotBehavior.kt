package emu.server.bot.behavior

/** Player-like game actions available to one automated client behavior. */
fun interface BotClient {
    suspend fun walkTo(x: Int, z: Int)
}

/** Drives one connected automated player without bypassing client packets. */
fun interface BotBehavior {
    suspend fun run(client: BotClient)
}

/** Creates independent behavior state for each connected automated player. */
fun interface BotBehaviorFactory {
    fun create(seed: Long): BotBehavior
}
