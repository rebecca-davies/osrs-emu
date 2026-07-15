package emu.game.script

import kotlin.reflect.KClass

/** Stable, typed identity for a named player queue script. */
data class PlayerQueueType<A : Any>(
    val name: String,
    val argumentType: KClass<A>,
) {
    init {
        require(name.isNotBlank()) { "a queue type name must not be blank" }
    }

    companion object {
        /** Creates a queue type whose script takes no content argument. */
        fun unit(name: String): PlayerQueueType<Unit> = PlayerQueueType(name, Unit::class)
    }
}
