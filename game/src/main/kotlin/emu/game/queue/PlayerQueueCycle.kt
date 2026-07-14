package emu.game.queue

/**
 * Authentic queue/timer slice of a player's main-cycle turn.
 *
 * Suspended-script resume happens immediately before this slice and interaction/movement happens
 * immediately after it. Within the slice the fixed order is primary, weak, normal timer, soft
 * timer, then engine queue.
 */
class PlayerQueueCycle(
    val queues: PlayerQueues = PlayerQueues(),
    val timers: PlayerTimers = PlayerTimers(),
) {
    /** Processes one player's queue/timer work for [currentTick]. */
    suspend fun process(
        currentTick: Long,
        canAccess: () -> Boolean,
        closeModal: suspend () -> Unit = {},
        loggingOut: Boolean = false,
    ) {
        queues.processPrimaryAndWeak(canAccess, closeModal, loggingOut)
        timers.process(PlayerTimerType.NORMAL, currentTick, canAccess)
        timers.process(PlayerTimerType.SOFT, currentTick, canAccess)
        queues.processEngine(canAccess)
    }
}
