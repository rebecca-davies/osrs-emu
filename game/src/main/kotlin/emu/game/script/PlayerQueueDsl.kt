package emu.game.script

import emu.game.player.Player
import emu.game.queue.LongActionLogout
import emu.game.queue.PlayerActionPriority

/** Queue operations available to compiled Kotlin player scripts. */
abstract class PlayerQueueDsl internal constructor(
    private val queuePlayer: Player,
    private val scripts: PlayerScriptRepository,
) {
    /** Adds a normal protected action to the primary queue. */
    fun queue(type: PlayerQueueType<Unit>, delayTicks: Int = 0) {
        enqueue(type, Unit, PlayerActionPriority.NORMAL, delayTicks)
    }

    /** Adds a typed normal protected action to the primary queue. */
    fun <A : Any> queue(type: PlayerQueueType<A>, argument: A, delayTicks: Int = 0) {
        enqueue(type, argument, PlayerActionPriority.NORMAL, delayTicks)
    }

    /** Adds a strong protected action that closes modals before processing. */
    fun strongQueue(type: PlayerQueueType<Unit>, delayTicks: Int = 0) {
        enqueue(type, Unit, PlayerActionPriority.STRONG, delayTicks)
    }

    /** Adds a typed strong protected action to the primary queue. */
    fun <A : Any> strongQueue(type: PlayerQueueType<A>, argument: A, delayTicks: Int = 0) {
        enqueue(type, argument, PlayerActionPriority.STRONG, delayTicks)
    }

    /** Adds a weak protected action that modal closure may clear. */
    fun weakQueue(type: PlayerQueueType<Unit>, delayTicks: Int = 0) {
        enqueue(type, Unit, PlayerActionPriority.WEAK, delayTicks)
    }

    /** Adds a typed weak protected action that modal closure may clear. */
    fun <A : Any> weakQueue(type: PlayerQueueType<A>, argument: A, delayTicks: Int = 0) {
        enqueue(type, argument, PlayerActionPriority.WEAK, delayTicks)
    }

    /** Adds an engine-generated action at the later player queue point. */
    fun engineQueue(type: PlayerQueueType<Unit>) {
        enqueue(type, Unit, PlayerActionPriority.ENGINE, delayTicks = 0)
    }

    /** Adds a typed engine-generated action at the later player queue point. */
    fun <A : Any> engineQueue(type: PlayerQueueType<A>, argument: A) {
        enqueue(type, argument, PlayerActionPriority.ENGINE, delayTicks = 0)
    }

    /** Adds a long action with explicit logout behavior. */
    fun longQueue(
        type: PlayerQueueType<Unit>,
        delayTicks: Int,
        logout: LongActionLogout,
    ) {
        val request = PlayerScriptRequest(scripts.require(type))
        queuePlayer.actionQueue.addLong(request, delayTicks, logout)
    }

    /** Adds a typed long action with explicit logout behavior. */
    fun <A : Any> longQueue(
        type: PlayerQueueType<A>,
        argument: A,
        delayTicks: Int,
        logout: LongActionLogout,
    ) {
        val request = PlayerScriptRequest(scripts.require(type), argument)
        queuePlayer.actionQueue.addLong(request, delayTicks, logout)
    }

    private fun <A : Any> enqueue(
        type: PlayerQueueType<A>,
        argument: A,
        priority: PlayerActionPriority,
        delayTicks: Int,
    ) {
        val request = PlayerScriptRequest(scripts.require(type), argument)
        queuePlayer.actionQueue.add(request, priority, delayTicks)
    }
}
