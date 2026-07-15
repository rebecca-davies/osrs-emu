package emu.server.world.runtime

import kotlinx.coroutines.Deferred

/**
 * Admission and removal signals for one submitted world participant.
 *
 * [playerIndex] resolves to the reserved index, or null when admission is rejected. [removed]
 * resolves after an admitted index is released, or immediately for a rejected registration.
 */
class WorldRegistration internal constructor(
    internal val playerIndex: Deferred<Int?>,
    internal val removed: Deferred<Unit>,
)
